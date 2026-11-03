import { auth } from '#lib/auth';
import { ApiError, badRequest, codeFor, handler, notFound } from '#lib/server/http';
import { encodeFlow, FLOW_COOKIE, flowCookieOptions, isChallenge } from '#lib/server/native-auth';
import { env } from '#lib/server/env';

const PROVIDERS = new Set(['apple', 'google']);

/**
 * Opened by a native app in a Custom Tab / browser sheet. Starts Better Auth's
 * social sign-in inside that browser (so its state cookie lives there) and
 * redirects to the provider; the flow ends at ../callback.
 *
 * `?code_challenge=` is required: base64url(SHA-256(verifier)), where the app
 * keeps the verifier and sends it to ../exchange. It is remembered in a signed
 * httpOnly cookie in this browser, so only this flow can finish at ../callback.
 */
export const GET = handler<{ provider: string }>(async ({ params, request, url, cookies }) => {
	if (!PROVIDERS.has(params.provider)) throw notFound('Unknown sign-in provider');
	const challenge = url.searchParams.get('code_challenge');
	if (!isChallenge(challenge)) throw badRequest('Missing code_challenge. Please update the app and try again.');
	const callbackURL = `${env.baseUrl}/api/v1/auth/native/callback`;
	const res = await auth.api.signInSocial({
		body: { provider: params.provider as 'apple' | 'google', callbackURL, errorCallbackURL: callbackURL, disableRedirect: true },
		headers: request.headers,
		asResponse: true
	});
	const data = (await res.json().catch(() => ({}))) as { url?: string; message?: string };
	// With asResponse, Better Auth returns errors (e.g. an unconfigured provider) instead of throwing.
	if (!res.ok || !data.url) throw new ApiError(codeFor(res.status), data.message ?? `${params.provider} sign-in isn't available`);
	const headers = new Headers({ location: data.url });
	for (const cookie of res.headers.getSetCookie()) headers.append('set-cookie', cookie);
	cookies.set(FLOW_COOKIE, encodeFlow(challenge), flowCookieOptions(url));
	return new Response(null, { status: 302, headers });
});
