import { APIError } from 'better-auth';
import { error } from '@sveltejs/kit';
import { auth } from '#lib/auth';
import { COLOR_THEMES, THEME_MODES, type ColorTheme, type ThemeMode } from '#lib/types';
import type { Handle, ServerInit } from '@sveltejs/kit/hooks';
import type { RequestEvent } from '@sveltejs/kit';
import { autoArchiveGroups } from '#lib/server/auto-archive';
import { REMINDER_CHECK_INTERVAL_MS, sendDueReminders } from '#lib/server/reminders';
import { promptNextPlans } from '#lib/server/rhythm';
import { deleteAbandonedUploads } from '#lib/server/attachments';
import { checkCaldavConnections, syncStaleConnections } from '#lib/server/calendars';
import { isForbiddenCrossSite, selfOrigins } from '#lib/server/csrf';
import { env } from '#lib/server/env';
import { kv } from '#lib/server/redis';
import { checkOtpEmail, checkRateLimit, tooManyRequests } from '#lib/server/rate-limit';
import { log, requestContext, withRequestContext } from '#lib/server/log';

const ALLOWED_ORIGINS = selfOrigins(env.baseUrl, env.trustedOrigins);

const CALENDAR_SYNC_INTERVAL_MS = 5 * 60 * 1000;
/** iCloud / CalDAV can't notify us, so their change tags are checked this often instead. */
const CALDAV_CHECK_INTERVAL_MS = 2 * 60 * 1000;
/** How often groups that archive themselves are checked for a passed event. */
const AUTO_ARCHIVE_INTERVAL_MS = 15 * 60 * 1000;
/** How often chat uploads that were never sent are cleared away (they're kept a day). */
const ABANDONED_UPLOADS_INTERVAL_MS = 60 * 60 * 1000;

/**
 * Keeps Google / Microsoft / CalDAV busy time fresh in the background, checks
 * CalDAV change tags in between, archives groups whose event has passed and deletes unsent chat uploads. Every instance runs the timers, but locks in the
 * shared store let only one of them run each per interval.
 */
export const init: ServerInit = () => {
	if (process.env.NODE_ENV === 'test') return;
	setInterval(async () => {
		try {
			const lockSeconds = CALENDAR_SYNC_INTERVAL_MS / 1000 - 10;
			if (!(await kv.setIfAbsent('lock:calendar-sync', String(process.pid), lockSeconds))) return;
			// Stop starting syncs well before the lock expires: one in flight can take
			// a minute (several provider calls with 15–20 s timeouts each).
			await syncStaleConnections(Date.now() + (lockSeconds - 90) * 1000);
		} catch (err) {
			log.error('calendars', 'background sync failed', { err });
		}
	}, CALENDAR_SYNC_INTERVAL_MS).unref();
	setInterval(async () => {
		try {
			const lockSeconds = CALDAV_CHECK_INTERVAL_MS / 1000 - 5;
			if (!(await kv.setIfAbsent('lock:caldav-check', String(process.pid), lockSeconds))) return;
			// A check is one request; one that finds a change runs a full sync, so leave room for that.
			await checkCaldavConnections(Date.now() + (lockSeconds - 45) * 1000);
		} catch (err) {
			log.error('calendars', 'CalDAV change check failed', { err });
		}
	}, CALDAV_CHECK_INTERVAL_MS).unref();
	setInterval(async () => {
		try {
			if (!(await kv.setIfAbsent('lock:abandoned-uploads', String(process.pid), ABANDONED_UPLOADS_INTERVAL_MS / 1000 - 10))) return;
			await deleteAbandonedUploads();
		} catch (err) {
			log.error('chat', 'deleting abandoned uploads failed', { err });
		}
	}, ABANDONED_UPLOADS_INTERVAL_MS).unref();
	setInterval(async () => {
		try {
			if (!(await kv.setIfAbsent('lock:auto-archive', String(process.pid), AUTO_ARCHIVE_INTERVAL_MS / 1000 - 10))) return;
			await autoArchiveGroups();
		} catch (err) {
			log.error('groups', 'auto-archive failed', { err });
		}
	}, AUTO_ARCHIVE_INTERVAL_MS).unref();
	setInterval(async () => {
		try {
			if (!(await kv.setIfAbsent('lock:reminders', String(process.pid), REMINDER_CHECK_INTERVAL_MS / 1000 - 10))) return;
			await sendDueReminders();
			await promptNextPlans();
		} catch (err) {
			log.error('reminders', 'sending failed', { err });
		}
	}, REMINDER_CHECK_INTERVAL_MS).unref();
};

const OTP_SEND_PATH = '/api/auth/email-otp/send-verification-otp';

/** A 429 response when the caller is over a limit, otherwise null. */
async function rateLimit(event: RequestEvent): Promise<Response | null> {
	if (!env.rateLimit) return null;
	const { pathname } = event.url;
	try {
		// Webhooks authenticate with a shared secret (or a per-channel token) and can arrive in bursts.
		// Chat attachments load by signed link, without a session, a screenful of photos at a time.
		const exempt = ['/api/v1/billing/', '/api/v1/calendars/push/', '/api/v1/attachments/'].some((p) => pathname.startsWith(p));
		if (pathname.startsWith('/api/v1/') && !exempt) {
			const decision = await checkRateLimit(kv, event.request.method, pathname, {
				userId: event.locals.user?.id ?? null,
				ip: event.getClientAddress()
			});
			if (!decision.allowed) return tooManyRequests(decision.retryAfter);
		} else if (pathname === OTP_SEND_PATH && event.request.method === 'POST') {
			const email = await event.request
				.clone()
				.json()
				.then((b: { email?: unknown }) => (typeof b.email === 'string' ? b.email : null))
				.catch(() => null);
			if (email) {
				const decision = await checkOtpEmail(kv, email);
				if (!decision.allowed) return tooManyRequests(decision.retryAfter);
			}
		}
	} catch (err) {
		// Fail open: a Redis outage shouldn't take the API down with it.
		log.warn('rate-limit', 'check failed', { err });
	}
	return null;
}

export const handle: Handle = async ({ event, resolve }) => {
	// Everything logged while handling this request carries its id (also sent back as X-Request-Id).
	const requestId = event.request.headers.get('x-request-id')?.slice(0, 64) || crypto.randomUUID();
	const response = await withRequestContext({ requestId, userId: null, method: event.request.method, path: event.url.pathname }, () =>
		handleRequest({ event, resolve })
	);
	try {
		response.headers.set('x-request-id', requestId);
	} catch {
		// Immutable headers.
	}
	return response;
};

const handleRequest: Handle = async ({ event, resolve }) => {
	if (isForbiddenCrossSite(event.request, event.url, ALLOWED_ORIGINS)) {
		return Response.json({ message: 'Cross-site request blocked', code: 'forbidden' }, { status: 403 });
	}

	// Resolves both cookie sessions (web) and `Authorization: Bearer` tokens (native).
	let session;
	try {
		session = await auth.api.getSession({ headers: event.request.headers });
	} catch (err) {
		if (err instanceof APIError) error(err.statusCode ?? 500, err.message);
		throw err;
	}
	event.locals.user = session?.user ?? null;
	const ctx = requestContext();
	if (ctx) ctx.userId = event.locals.user?.id ?? null;
	event.locals.session = session?.session ?? null;

	const limited = await rateLimit(event);
	if (limited) return limited;

	// Render the saved appearance during SSR so there's no flash. Values are
	// validated so an unexpected stored value can't be injected into markup.
	const mode: ThemeMode = THEME_MODES.find((m) => m === event.locals.user?.themeMode) ?? 'system';
	const theme: ColorTheme = COLOR_THEMES.find((t) => t === event.locals.user?.colorTheme) ?? 'dawn';
	const response = await resolve(event, {
		transformPageChunk: ({ html }) => html.replace('data-mode="system" data-theme="dawn"', `data-mode="${mode}" data-theme="${theme}"`)
	});
	setSecurityHeaders(response.headers);
	return response;
};

/**
 * Headers every response gets (the Content-Security-Policy itself is set by SvelteKit, see
 * vite.config.ts). HSTS only in production, so plain-HTTP dev on localhost isn't pinned to HTTPS.
 */
function setSecurityHeaders(headers: Headers) {
	const set = (name: string, value: string) => {
		// Some responses (redirects, fetched files) have immutable headers; skip those quietly.
		try {
			if (!headers.has(name)) headers.set(name, value);
		} catch {
			// Immutable.
		}
	};
	if (process.env.NODE_ENV === 'production') set('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
	set('X-Content-Type-Options', 'nosniff');
	set('X-Frame-Options', 'DENY');
	set('Referrer-Policy', 'strict-origin-when-cross-origin');
	// Location is for sharing a place in chat; nothing uses the camera, microphone or payment APIs directly.
	set('Permissions-Policy', 'camera=(), microphone=(), geolocation=(self), interest-cohort=()');
}
