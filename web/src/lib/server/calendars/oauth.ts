/** OAuth 2 authorization-code flow for Google Calendar and Microsoft Graph. */
import { createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import { env, signingSecret } from '../env';
import { badRequest, forbidden } from '../http';
import { kv } from '../redis';
import { safeRedirectPath } from '#lib/client/safe-path';
import { ReauthRequired, type OAuthCredentials } from './types';

export type OAuthProvider = 'google' | 'microsoft';

interface ProviderConfig {
	authorizeUrl: string;
	tokenUrl: string;
	scopes: string[];
	clientId: string | null;
	clientSecret: string | null;
	extraParams: Record<string, string>;
}

const PROVIDERS: Record<OAuthProvider, () => ProviderConfig> = {
	google: () => ({
		authorizeUrl: 'https://accounts.google.com/o/oauth2/v2/auth',
		tokenUrl: 'https://oauth2.googleapis.com/token',
		scopes: ['https://www.googleapis.com/auth/calendar.readonly'],
		clientId: env.google.clientIds[0] ?? null,
		clientSecret: env.google.clientSecret,
		// Offline access + consent so Google always returns a refresh token.
		extraParams: { access_type: 'offline', prompt: 'consent', include_granted_scopes: 'true' }
	}),
	microsoft: () => ({
		authorizeUrl: 'https://login.microsoftonline.com/common/oauth2/v2.0/authorize',
		tokenUrl: 'https://login.microsoftonline.com/common/oauth2/v2.0/token',
		scopes: ['offline_access', 'User.Read', 'Calendars.Read'],
		clientId: env.microsoft.clientId,
		clientSecret: env.microsoft.clientSecret,
		extraParams: { prompt: 'select_account' }
	})
};

export const isOAuthProvider = (p: string): p is OAuthProvider => p === 'google' || p === 'microsoft';

function config(provider: OAuthProvider): ProviderConfig & { clientId: string; clientSecret: string } {
	const c = PROVIDERS[provider]();
	if (!c.clientId || !c.clientSecret) throw badRequest(`${provider} calendar isn't configured on this server`);
	return c as ProviderConfig & { clientId: string; clientSecret: string };
}

export const callbackUrl = (provider: OAuthProvider) => `${env.baseUrl}/api/v1/calendars/oauth/${provider}/callback`;

// ─── Signed state ─────────────────────────────────────────────────────────
// The state parameter carries who started the flow and where to return, signed
// so it can't be forged, and expires after 10 minutes.

interface State {
	userId: string;
	provider: OAuthProvider;
	redirect: string;
	exp: number;
	nonce: string;
}

const signature = (payload: string) => createHmac('sha256', signingSecret()).update(payload).digest('base64url');

/** Only same-origin app paths and the native app scheme are valid return targets. */
export function safeRedirect(redirect: string | null): string {
	// eslint-disable-next-line no-control-regex
	if (redirect?.startsWith('availabliss://') && !/[\\\u0000-\u001f\u007f]/.test(redirect)) return redirect;
	return safeRedirectPath(redirect) ?? '/app/calendars';
}

export function encodeState(state: Omit<State, 'exp' | 'nonce'>): string {
	const payload = Buffer.from(
		JSON.stringify({ ...state, exp: Date.now() + 10 * 60 * 1000, nonce: randomBytes(8).toString('hex') })
	).toString('base64url');
	return `${payload}.${signature(payload)}`;
}

export function decodeState(raw: string | null): State {
	const [payload, sig] = (raw ?? '').split('.');
	const expected = payload ? signature(payload) : '';
	if (!payload || !sig || sig.length !== expected.length || !timingSafeEqual(Buffer.from(sig), Buffer.from(expected))) {
		throw badRequest('Invalid sign-in state');
	}
	const state = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8')) as State;
	if (state.exp < Date.now()) throw badRequest('This connection attempt expired, please try again');
	return state;
}

/** Marks the state's nonce used. A state can only finish one connection. */
export async function consumeState(state: State): Promise<void> {
	const fresh = await kv.setIfAbsent(`oauth:nonce:${state.nonce}`, '1', 15 * 60);
	if (!fresh) throw badRequest('This connection attempt was already used, please try again');
}

// ─── Native hand-off ──────────────────────────────────────────────────────
// A native app opens consent in a browser sheet that can't prove who is signed
// in, so the callback never links the calendar itself. It parks the provider's
// code under a one-time code and redirects to the app, which finishes with an
// authenticated POST /calendars/oauth/:provider/complete. Only the user who
// started the flow can complete it, so a consent link sent to someone else
// can't attach their calendar to the sender's account.

interface PendingLink {
	userId: string;
	provider: OAuthProvider;
	code: string;
}

const pendingKey = (handoff: string) => `oauth:pending:${handoff}`;

export async function parkPendingLink(link: PendingLink): Promise<string> {
	const handoff = randomBytes(24).toString('base64url');
	await kv.set(pendingKey(handoff), JSON.stringify(link), 10 * 60);
	return handoff;
}

/** Takes (once) the parked provider code, checking it belongs to `userId` and `provider`. */
export async function takePendingLink(handoff: string, userId: string, provider: OAuthProvider): Promise<string> {
	const raw = await kv.take(pendingKey(handoff));
	if (!raw) throw badRequest('This connection attempt expired, please try again');
	const link = JSON.parse(raw) as PendingLink;
	if (link.userId !== userId || link.provider !== provider) {
		throw forbidden('This calendar connection was started by a different account');
	}
	return link.code;
}

// ─── Flow ─────────────────────────────────────────────────────────────────

export function authorizationUrl(provider: OAuthProvider, state: string): string {
	const c = config(provider);
	const url = new URL(c.authorizeUrl);
	url.search = new URLSearchParams({
		client_id: c.clientId,
		redirect_uri: callbackUrl(provider),
		response_type: 'code',
		scope: c.scopes.join(' '),
		state,
		...c.extraParams
	}).toString();
	return url.toString();
}

async function tokenRequest(provider: OAuthProvider, params: Record<string, string>): Promise<OAuthCredentials> {
	const c = config(provider);
	const res = await fetch(c.tokenUrl, {
		method: 'POST',
		headers: { 'content-type': 'application/x-www-form-urlencoded' },
		body: new URLSearchParams({ client_id: c.clientId, client_secret: c.clientSecret, ...params })
	});
	const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
	if (!res.ok) {
		if (data.error === 'invalid_grant') throw new ReauthRequired(String(data.error_description ?? 'invalid_grant'));
		throw new Error(`${provider} token request failed (${res.status}): ${JSON.stringify(data)}`);
	}
	return {
		accessToken: String(data.access_token),
		refreshToken: typeof data.refresh_token === 'string' ? data.refresh_token : null,
		expiresAt: Date.now() + Number(data.expires_in ?? 3600) * 1000
	};
}

export async function exchangeCode(provider: OAuthProvider, code: string): Promise<OAuthCredentials> {
	return tokenRequest(provider, { grant_type: 'authorization_code', code, redirect_uri: callbackUrl(provider) });
}

/** Refreshes when the access token expires within five minutes. Returns null if still valid. */
export async function refreshIfNeeded(provider: OAuthProvider, creds: OAuthCredentials): Promise<OAuthCredentials | null> {
	if (creds.expiresAt - Date.now() > 5 * 60 * 1000) return null;
	if (!creds.refreshToken) throw new ReauthRequired('No refresh token');
	const fresh = await tokenRequest(provider, { grant_type: 'refresh_token', refresh_token: creds.refreshToken });
	// Providers may omit the refresh token on refresh; keep the old one.
	return { ...fresh, refreshToken: fresh.refreshToken ?? creds.refreshToken };
}

/** A provider answered with an error status other than 401. */
export class HttpError extends Error {
	constructor(
		message: string,
		readonly status: number
	) {
		super(message);
	}
}

/** GET/POST JSON with a bearer token, mapping 401 to ReauthRequired. */
export async function bearerFetch<T>(url: string, token: string, init: RequestInit = {}): Promise<T> {
	const res = await fetch(url, {
		...init,
		headers: { authorization: `Bearer ${token}`, accept: 'application/json', ...init.headers },
		signal: AbortSignal.timeout(15_000)
	});
	if (res.status === 401) throw new ReauthRequired(`${url} → 401`);
	if (res.status === 204) return undefined as T;
	if (!res.ok) throw new HttpError(`${url} → ${res.status}: ${await res.text().catch(() => '')}`, res.status);
	return (await res.json()) as T;
}
