import { APIError } from 'better-auth';
import { error } from '@sveltejs/kit';
import { auth } from '#lib/auth';
import { COLOR_THEMES, THEME_MODES, type ColorTheme, type ThemeMode } from '#lib/types';
import type { Handle, ServerInit } from '@sveltejs/kit/hooks';
import type { RequestEvent } from '@sveltejs/kit';
import { syncStaleConnections } from '#lib/server/calendars';
import { isForbiddenCrossSite, selfOrigins } from '#lib/server/csrf';
import { env } from '#lib/server/env';
import { kv } from '#lib/server/redis';
import { checkOtpEmail, checkRateLimit, tooManyRequests } from '#lib/server/rate-limit';

const ALLOWED_ORIGINS = selfOrigins(env.baseUrl, env.trustedOrigins);

const CALENDAR_SYNC_INTERVAL_MS = 5 * 60 * 1000;

/**
 * Keeps Google / Microsoft / CalDAV busy time fresh in the background. Every
 * instance runs the timer, but a lock in the shared store lets only one of them
 * sync per interval.
 */
export const init: ServerInit = () => {
	if (process.env.NODE_ENV === 'test') return;
	setInterval(async () => {
		try {
			const lockSeconds = CALENDAR_SYNC_INTERVAL_MS / 1000 - 10;
			if (!(await kv.setIfAbsent('lock:calendar-sync', String(process.pid), lockSeconds))) return;
			await syncStaleConnections();
		} catch (err) {
			console.warn('[calendars] background sync failed', err);
		}
	}, CALENDAR_SYNC_INTERVAL_MS).unref();
};

const OTP_SEND_PATH = '/api/auth/email-otp/send-verification-otp';

/** A 429 response when the caller is over a limit, otherwise null. */
async function rateLimit(event: RequestEvent): Promise<Response | null> {
	if (!env.rateLimit) return null;
	const { pathname } = event.url;
	try {
		if (pathname.startsWith('/api/v1/')) {
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
		console.warn('[rate-limit] check failed', err);
	}
	return null;
}

export const handle: Handle = async ({ event, resolve }) => {
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
	event.locals.session = session?.session ?? null;

	const limited = await rateLimit(event);
	if (limited) return limited;

	// Render the saved appearance during SSR so there's no flash. Values are
	// validated so an unexpected stored value can't be injected into markup.
	const mode: ThemeMode = THEME_MODES.find((m) => m === event.locals.user?.themeMode) ?? 'system';
	const theme: ColorTheme = COLOR_THEMES.find((t) => t === event.locals.user?.colorTheme) ?? 'dawn';
	return resolve(event, {
		transformPageChunk: ({ html }) =>
			html.replace('data-mode="system" data-theme="dawn"', `data-mode="${mode}" data-theme="${theme}"`)
	});
};
