import { APIError } from 'better-auth';
import { error } from '@sveltejs/kit';
import { auth } from '#lib/auth';
import { COLOR_THEMES, THEME_MODES, type ColorTheme, type ThemeMode } from '#lib/types';
import type { Handle, ServerInit } from '@sveltejs/kit/hooks';
import { syncStaleConnections } from '#lib/server/calendars';
import { isForbiddenCrossSite, selfOrigins } from '#lib/server/csrf';
import { env } from '#lib/server/env';

const ALLOWED_ORIGINS = selfOrigins(env.baseUrl, env.trustedOrigins);

const CALENDAR_SYNC_INTERVAL_MS = 5 * 60 * 1000;

/** Keeps Google / Microsoft / CalDAV busy time fresh in the background. */
export const init: ServerInit = () => {
	if (process.env.NODE_ENV === 'test') return;
	setInterval(() => {
		syncStaleConnections().catch((err) => console.warn('[calendars] background sync failed', err));
	}, CALENDAR_SYNC_INTERVAL_MS).unref();
};

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

	// Render the saved appearance during SSR so there's no flash. Values are
	// validated so an unexpected stored value can't be injected into markup.
	const mode: ThemeMode = THEME_MODES.find((m) => m === event.locals.user?.themeMode) ?? 'system';
	const theme: ColorTheme = COLOR_THEMES.find((t) => t === event.locals.user?.colorTheme) ?? 'dawn';
	return resolve(event, {
		transformPageChunk: ({ html }) =>
			html.replace('data-mode="system" data-theme="dawn"', `data-mode="${mode}" data-theme="${theme}"`)
	});
};
