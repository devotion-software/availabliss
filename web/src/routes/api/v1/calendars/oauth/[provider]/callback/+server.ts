import { handler, notFound, redirectTo } from '#lib/server/http';
import { consumeState, decodeState, isOAuthProvider, parkPendingLink } from '#lib/server/calendars/oauth';
import { connectOAuth } from '#lib/server/calendars';

/** Appends a query parameter to an app path or native deep link. */
const withParam = (target: string, key: string, value: string) =>
	`${target}${target.includes('?') ? '&' : '?'}${key}=${encodeURIComponent(value)}`;

/**
 * Not authenticated by itself: whoever finishes consent must be shown to be the
 * user in the signed state before anything is linked. Web flows check the
 * session cookie here; native flows hand off to an authenticated POST …/complete.
 */
export const GET = handler<{ provider: string }>(async ({ params, url, locals }) => {
	if (!isOAuthProvider(params.provider)) throw notFound('Unknown calendar provider');
	const state = decodeState(url.searchParams.get('state'));
	const code = url.searchParams.get('code');
	if (!code || state.provider !== params.provider) {
		redirectTo(withParam(state.redirect, 'error', url.searchParams.get('error') ?? 'cancelled'));
	}
	await consumeState(state);

	if (state.redirect.startsWith('availabliss://')) {
		const handoff = await parkPendingLink({ userId: state.userId, provider: params.provider, code });
		redirectTo(withParam(withParam(state.redirect, 'complete', handoff), 'provider', params.provider));
	}

	if (locals.user?.id !== state.userId) {
		redirectTo(withParam(state.redirect, 'error', 'wrong_account'));
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
