import { authed, notFound, redirectTo } from '#lib/server/http';
import { authorizationUrl, encodeState, isOAuthProvider, safeRedirect } from '#lib/server/calendars/oauth';

export const GET = authed<{ provider: string }>(({ params, url, userId }) => {
	if (!isOAuthProvider(params.provider)) throw notFound('Unknown calendar provider');
	const state = encodeState({ userId, provider: params.provider, redirect: safeRedirect(url.searchParams.get('redirect')) });
	redirectTo(authorizationUrl(params.provider, state));
});
