/**
 * Hands a web-flow sign-in (e.g. Sign in with Apple on Android) back to a native
 * app. The browser finishes on our callback, which redirects to the app with a
 * short-lived one-time code; the app exchanges the code for its bearer token, so
 * the token itself never appears in a URL. Codes live in memory (single node).
 */
import { randomBytes } from 'node:crypto';
import { badRequest } from './http';

const TTL_MS = 2 * 60 * 1000;
const codes = new Map<string, { token: string; expires: number }>();

export const NATIVE_CALLBACK = 'availabliss://auth/callback';

export function issueCode(token: string): string {
	const now = Date.now();
	for (const [code, entry] of codes) if (entry.expires < now) codes.delete(code);
	const code = randomBytes(24).toString('base64url');
	codes.set(code, { token, expires: now + TTL_MS });
	return code;
}

export function redeemCode(code: string): string {
	const entry = codes.get(code);
	codes.delete(code);
	if (!entry || entry.expires < Date.now()) throw badRequest('This sign-in link has expired, please try again');
	return entry.token;
}
