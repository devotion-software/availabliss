import { handler, notFound, redirectTo } from '#lib/server/http';
import { decodeState, isOAuthProvider } from '#lib/server/calendars/oauth';
import { connectOAuth } from '#lib/server/calendars';

/** Appends a query parameter to an app path or native deep link. */
const withParam = (target: string, key: string, value: string) =>
	`${target}${target.includes('?') ? '&' : '?'}${key}=${encodeURIComponent(value)}`;

export const GET = handler<{ provider: string }>(async ({ params, url }) => {
	if (!isOAuthProvider(params.provider)) throw notFound('Unknown calendar provider');
	const state = decodeState(url.searchParams.get('state'));
	const code = url.searchParams.get('code');
	if (!code || state.provider !== params.provider) {
		redirectTo(withParam(state.redirect, 'error', url.searchParams.get('error') ?? 'cancelled'));
	}
	let target: string;
	try {
		target = withParam(state.redirect, 'connected', await connectOAuth(state.userId, params.provider, code));
	} catch (err) {
		console.warn('[calendars] oauth callback failed', err);
		target = withParam(state.redirect, 'error', 'connection_failed');
	}
	redirectTo(target);
});
