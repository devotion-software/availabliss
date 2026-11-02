/**
 * Tiny API client for the dev scripts (smoke test, demo seeding). Talks to a
 * running dev server and reads sign-in codes from Mailpit, exactly like the apps.
 */
export const BASE = process.env.BASE_URL ?? 'http://localhost:5190';
export const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8027';

export interface Response<T> {
	status: number;
	data: T;
	headers: Headers;
}

export async function call<T = any>(token: string | null, method: string, path: string, body?: unknown): Promise<Response<T>> {
	const res = await fetch(`${BASE}${path}`, {
		method,
		headers: {
			...(token && { authorization: `Bearer ${token}` }),
			...(body !== undefined && { 'content-type': 'application/json' }),
			origin: BASE
		},
		body: body === undefined ? undefined : JSON.stringify(body)
	});
	const text = await res.text();
	let data: unknown = text;
	try {
		data = text ? JSON.parse(text) : null;
	} catch {
		// Not JSON (e.g. an .ics file); keep the text.
	}
	return { status: res.status, data: data as T, headers: res.headers };
}

/** The newest 6-digit sign-in code Mailpit received for `email`. */
export async function otpFor(email: string): Promise<string> {
	for (let i = 0; i < 40; i++) {
		const res = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
		const { messages } = (await res.json()) as { messages: { Snippet: string }[] };
		const code = messages[0]?.Snippet.match(/\b(\d{6})\b/)?.[1];
		if (code) return code;
		await Bun.sleep(250);
	}
	throw new Error(`No sign-in code arrived for ${email}`);
}

export interface SignedIn {
	token: string;
	id: string;
	email: string;
}

/** Signs in with an emailed code (creating the account if new) and completes the profile. */
export async function signIn(email: string, name: string): Promise<SignedIn> {
	await call(null, 'POST', '/api/auth/email-otp/send-verification-otp', { email, type: 'sign-in' });
	const res = await call(null, 'POST', '/api/auth/sign-in/email-otp', { email, otp: await otpFor(email) });
	const token = res.headers.get('set-auth-token');
	if (res.status !== 200 || !token) throw new Error(`Sign-in failed for ${email}: ${JSON.stringify(res.data)}`);
	await call(token, 'PATCH', '/api/v1/me', { name, timezone: 'Europe/London', country: 'GB', subdivision: 'GB-ENG', onboarded: true });
	return { token, id: res.data.user.id, email };
}

/** `YYYY-MM-DD` `n` days after `date`. */
export const addDays = (date: string, n: number) =>
	new Date(Date.parse(`${date}T00:00:00Z`) + n * 86_400_000).toISOString().slice(0, 10);

export const todayUtc = () => new Date().toISOString().slice(0, 10);

/** The first date on or after `from` that falls on `weekday` (0 = Sunday … 6 = Saturday, as in JS). */
export function nextWeekday(from: string, weekday: number): string {
	for (let i = 0; i < 7; i++) {
		const date = addDays(from, i);
		if (new Date(`${date}T12:00:00Z`).getUTCDay() === weekday) return date;
	}
	throw new Error('unreachable');
}
