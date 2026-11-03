const BASE = 'http://same-origin.invalid';

/**
 * Only same-origin absolute paths are allowed as redirect destinations
 * (post-sign-in `?next=`, OAuth return targets). Returns the normalised
 * path + query + hash, or null.
 *
 * Browsers treat `\` like `/` and strip tab / CR / LF from URLs, so `/\evil.com`
 * and `/<tab>/evil.com` both become `//evil.com`. Those characters are rejected
 * outright, and the result must still resolve to our own origin.
 */
export function safeRedirectPath(value: string | null | undefined): string | null {
	// eslint-disable-next-line no-control-regex
	if (!value || !value.startsWith('/') || /[\\\u0000-\u001f\u007f]/.test(value)) return null;
	let url: URL;
	try {
		url = new URL(value, BASE);
	} catch {
		return null;
	}
	if (url.origin !== BASE) return null;
	return url.pathname + url.search + url.hash;
}
