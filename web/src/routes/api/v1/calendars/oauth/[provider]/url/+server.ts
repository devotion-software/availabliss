import { z } from 'zod';
import { authed, body, json, notFound } from '#lib/server/http';
import { authorizationUrl, encodeState, isOAuthProvider, safeRedirect } from '#lib/server/calendars/oauth';

/**
 * For native apps: returns the provider's consent URL with the signed state
 * already embedded, so it can be opened in a browser sheet that can't send the
 * app's bearer token.
 */
export const POST = authed<{ provider: string }>(async ({ event, params, userId }) => {
	if (!isOAuthProvider(params.provider)) throw notFound('Unknown calendar provider');
	const { redirect } = await body(event, z.object({ redirect: z.string().max(200).optional() }));
	const state = encodeState({ userId, provider: params.provider, redirect: safeRedirect(redirect ?? null) });
	return json({ url: authorizationUrl(params.provider, state) });
});
