/**
 * Links to stored pictures (profile pictures, group photos, event covers). The files are private:
 * they're only served by /media/<key> with a signature that expires, like chat attachments, so a
 * link someone copied stops working after a week and nobody can fetch a picture without one.
 *
 * The database keeps where each picture lives (`<BETTER_AUTH_URL>/media/<key>`, or for pictures
 * stored before this, `<R2_PUBLIC_URL>/<key>`); `mediaUrl` turns that into a signed link each time
 * it's sent. Pictures from elsewhere (a Google profile photo) pass through untouched.
 */
import { createHmac, hkdfSync, timingSafeEqual } from 'node:crypto';
import { env } from './env';

/** `avatars|groups|events/<owner id>/<token>.jpg`: the only keys /media serves. */
export const MEDIA_KEY = /^(avatars|groups|events)\/[\w-]+\/[\w-]+\.jpg$/;

const DAY_SECONDS = 86_400;
/** Links last 7–8 days and stay the same all day, so apps and browsers can cache what they load. */
const LINK_DAYS = 8;

/** Where a newly stored picture is recorded as living (signed when it's sent out). */
export const storedUrlOf = (key: string) => `${env.baseUrl}/media/${key}`;

/** The storage key behind one of our picture URLs (signed or not), or null for anyone else's. */
export function keyOf(url: string | null): string | null {
	if (!url) return null;
	for (const base of [env.r2.publicUrl, `${env.baseUrl}/media`]) {
		if (base && url.startsWith(`${base}/`)) {
			const key = url.slice(base.length + 1).split('?')[0];
			return MEDIA_KEY.test(key) ? key : null;
		}
	}
	return null;
}

let signingKey: Buffer | null = null;
function sign(key: string, expires: number): string {
	if (!signingKey) {
		if (!env.secretsKey) throw new Error('SECRETS_KEY (or BETTER_AUTH_SECRET) must be set');
		signingKey = Buffer.from(hkdfSync('sha256', env.secretsKey, 'availabliss', 'media:v1', 32));
	}
	return createHmac('sha256', signingKey).update(`${key}:${expires}`).digest('base64url').slice(0, 32);
}

/** A signed link to one of our stored pictures; anything else (or null) is returned as it is. */
export function mediaUrl(stored: string | null, now = Date.now()): string | null {
	const key = keyOf(stored);
	if (!key) return stored;
	const expires = (Math.floor(now / 1000 / DAY_SECONDS) + LINK_DAYS) * DAY_SECONDS;
	return `${env.baseUrl}/media/${key}?e=${expires}&s=${sign(key, expires)}`;
}

/** Whether `e` / `s` from a link are a valid, unexpired signature for `key`. */
export function verifyMediaSignature(key: string, e: string | null, s: string | null, now = Date.now()): boolean {
	const expires = Number(e);
	if (!s || !Number.isInteger(expires) || expires * 1000 < now) return false;
	const expected = Buffer.from(sign(key, expires));
	const given = Buffer.from(s);
	return given.length === expected.length && timingSafeEqual(given, expected);
}
