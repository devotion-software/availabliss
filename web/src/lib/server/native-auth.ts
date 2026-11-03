/**
 * Hands a web-flow sign-in (e.g. Sign in with Apple on Android) back to a native
 * app. The browser finishes on our callback, which redirects to the app with a
 * short-lived one-time code; the app exchanges the code for its bearer token, so
 * the token itself never appears in a URL. Codes live in the shared store
 * (Redis), so the exchange can land on any instance.
 *
 * The code is bound to the app that started the flow, PKCE style. Any app can
 * register the `availabliss://` scheme, and a web page can send a signed-in
 * browser to the callback, so the callback alone proves nothing:
 *  - start takes a `code_challenge` (base64url SHA-256 of a random verifier the
 *    app keeps) and stores it, signed, in an httpOnly cookie in that browser;
 *  - the callback only issues a code when that cookie is present and the session
 *    was created after the flow started (i.e. by this sign-in, not a browser that
 *    was already signed in);
 *  - exchange needs the verifier, which never leaves the app.
 */
import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import { badRequest } from './http';
import { env } from './env';
import { kv } from './redis';

const TTL_SECONDS = 2 * 60;
const FLOW_SECONDS = 10 * 60;
const key = (code: string) => `native-code:${code}`;

export const NATIVE_CALLBACK = 'availabliss://auth/callback';
export const FLOW_COOKIE = 'av_native_flow';
export const FLOW_COOKIE_PATH = '/api/v1/auth/native';

/** base64url(SHA-256(verifier)) — RFC 7636 S256. */
export const challengeFor = (verifier: string) => createHash('sha256').update(verifier).digest('base64url');

/** A challenge is a base64url SHA-256 digest: exactly 43 characters. */
export const isChallenge = (value: string | null): value is string => !!value && /^[A-Za-z0-9_-]{43}$/.test(value);

// ── Flow cookie ─────────────────────────────────────────────────────────────

interface Flow {
	challenge: string;
	startedAt: number;
}

const sign = (payload: string) => createHmac('sha256', env.authSecret ?? 'dev').update(`native-flow:${payload}`).digest('base64url');

export function encodeFlow(challenge: string, now = Date.now()): string {
	const payload = Buffer.from(JSON.stringify({ challenge, startedAt: now } satisfies Flow)).toString('base64url');
	return `${payload}.${sign(payload)}`;
}

export function decodeFlow(raw: string | undefined, now = Date.now()): Flow | null {
	const [payload, sig] = (raw ?? '').split('.');
	if (!payload || !sig) return null;
	const expected = sign(payload);
	if (sig.length !== expected.length || !timingSafeEqual(Buffer.from(sig), Buffer.from(expected))) return null;
	try {
		const flow = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8')) as Flow;
		if (!isChallenge(flow.challenge) || typeof flow.startedAt !== 'number') return null;
		if (now - flow.startedAt > FLOW_SECONDS * 1000) return null;
		return flow;
	} catch {
		return null;
	}
}

/** `secure` follows the request so the Android emulator's plain-http dev server still works. */
export const flowCookieOptions = (url: URL) => ({ path: FLOW_COOKIE_PATH, httpOnly: true, secure: url.protocol === 'https:', sameSite: 'lax' as const, maxAge: FLOW_SECONDS });

/**
 * Whether `session` was created by the flow in `flow` (allowing a little clock
 * skew between instances), rather than being an older browser session.
 */
export const sessionFromFlow = (flow: Flow, sessionCreatedAt: Date) => sessionCreatedAt.getTime() >= flow.startedAt - 5_000;

// ── Codes ───────────────────────────────────────────────────────────────────

export async function issueCode(token: string, challenge: string): Promise<string> {
	const code = randomBytes(24).toString('base64url');
	await kv.set(key(code), JSON.stringify({ token, challenge }), TTL_SECONDS);
	return code;
}

/** Single use: the code is deleted as it is read, even if the verifier is wrong. */
export async function redeemCode(code: string, verifier: string): Promise<string> {
	const raw = await kv.take(key(code));
	if (!raw) throw badRequest('This sign-in link has expired, please try again');
	const { token, challenge } = JSON.parse(raw) as { token: string; challenge: string };
	const actual = challengeFor(verifier);
	if (actual.length !== challenge.length || !timingSafeEqual(Buffer.from(actual), Buffer.from(challenge))) {
		throw badRequest("This sign-in wasn't started by this app, please try again");
	}
	return token;
}
