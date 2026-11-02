/**
 * Cross-site request forgery guard.
 *
 * SvelteKit's built-in check rejects every body-less POST / DELETE without a
 * matching `Origin` header, which breaks the native apps (they send bearer
 * tokens and no `Origin`) in production only, since the check is skipped in dev.
 * It is turned off in vite.config.ts and replaced by this narrower rule:
 *
 * - Only mutating methods are checked.
 * - Requests carrying `Authorization: Bearer` are exempt: a browser never attaches
 *   that header on its own, so it can't be forged cross-site.
 * - Better Auth's own routes (`/api/auth/*`) are exempt: Better Auth validates
 *   origins itself, and Sign in with Apple POSTs its callback from appleid.apple.com.
 * - Otherwise the `Origin` must be one of ours. With no `Origin` (non-browser
 *   clients), a browser's `Sec-Fetch-Site: cross-site` still rejects it.
 */
const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export function isForbiddenCrossSite(request: Request, url: URL, allowedOrigins: readonly string[]): boolean {
	if (!MUTATING.has(request.method)) return false;
	if (url.pathname.startsWith('/api/auth/')) return false;
	if (/^bearer\s/i.test(request.headers.get('authorization') ?? '')) return false;

	const origin = request.headers.get('origin');
	if (origin) return origin !== url.origin && !allowedOrigins.includes(origin);
	return request.headers.get('sec-fetch-site') === 'cross-site';
}

/** Origins treated as "self": the public URL plus any extra trusted origins. */
export function selfOrigins(baseUrl: string, trusted: readonly string[]): string[] {
	const origins = new Set<string>(trusted);
	try {
		origins.add(new URL(baseUrl).origin);
	} catch {
		// An invalid BETTER_AUTH_URL is reported elsewhere; fall back to the request origin.
	}
	return [...origins];
}
