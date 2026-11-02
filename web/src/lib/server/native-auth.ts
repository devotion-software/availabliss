/**
 * Hands a web-flow sign-in (e.g. Sign in with Apple on Android) back to a native
 * app. The browser finishes on our callback, which redirects to the app with a
 * short-lived one-time code; the app exchanges the code for its bearer token, so
 * the token itself never appears in a URL. Codes live in the shared store
 * (Redis), so the exchange can land on any instance.
 */
import { randomBytes } from 'node:crypto';
import { badRequest } from './http';
import { kv } from './redis';

const TTL_SECONDS = 2 * 60;
const key = (code: string) => `native-code:${code}`;

export const NATIVE_CALLBACK = 'availabliss://auth/callback';

export async function issueCode(token: string): Promise<string> {
	const code = randomBytes(24).toString('base64url');
	await kv.set(key(code), token, TTL_SECONDS);
	return code;
}

/** Single use: the code is deleted as it is read. */
export async function redeemCode(code: string): Promise<string> {
	const token = await kv.take(key(code));
	if (!token) throw badRequest('This sign-in link has expired, please try again');
	return token;
}
