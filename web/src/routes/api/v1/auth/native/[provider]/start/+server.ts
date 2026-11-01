import { auth } from '#lib/auth';
import { ApiError, codeFor, handler, notFound } from '#lib/server/http';
import { env } from '#lib/server/env';

const PROVIDERS = new Set(['apple', 'google']);

/**
 * Opened by a native app in a Custom Tab / browser sheet. Starts Better Auth's
 * social sign-in inside that browser (so its state cookie lives there) and
 * redirects to the provider; the flow ends at ../callback.
 */
export const GET = handler<{ provider: string }>(async ({ params, request }) => {
	if (!PROVIDERS.has(params.provider)) throw notFound('Unknown sign-in provider');
	const callbackURL = `${env.baseUrl}/api/v1/auth/native/callback`;
	const res = await auth.api.signInSocial({
		body: { provider: params.provider as 'apple' | 'google', callbackURL, errorCallbackURL: callbackURL, disableRedirect: true },
		headers: request.headers,
		asResponse: true
	});
	const data = (await res.json().catch(() => ({}))) as { url?: string; message?: string };
	// With asResponse, Better Auth returns errors (e.g. an unconfigured provider) instead of throwing.
	if (!res.ok || !data.url) throw new ApiError(codeFor(res.status), data.message ?? `${params.provider} sign-in isn't available`);
	const url = data.url;
	const headers = new Headers({ location: url });
	for (const cookie of res.headers.getSetCookie()) headers.append('set-cookie', cookie);
	return new Response(null, { status: 302, headers });
});
