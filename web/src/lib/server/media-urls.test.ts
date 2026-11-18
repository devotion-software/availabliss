import { describe, expect, test } from 'bun:test';
import { env } from './env';
import { keyOf, mediaUrl, storedUrlOf, verifyMediaSignature } from './media-urls';

const KEY = 'avatars/user-1/abcDEF123.jpg';

describe('picture links', () => {
	test('our pictures are sent as signed /media links; others pass through', () => {
		const url = mediaUrl(storedUrlOf(KEY))!;
		expect(url.startsWith(`${env.baseUrl}/media/${KEY}?e=`)).toBe(true);
		expect(keyOf(url)).toBe(KEY);
		expect(mediaUrl('https://lh3.googleusercontent.com/a/photo')).toBe('https://lh3.googleusercontent.com/a/photo');
		expect(mediaUrl(null)).toBeNull();
	});

	test('the same link all day, so it can be cached', () => {
		const morning = Date.parse('2031-01-08T08:00:00Z');
		expect(mediaUrl(storedUrlOf(KEY), morning)).toBe(mediaUrl(storedUrlOf(KEY), morning + 10 * 3600_000));
	});

	test('a signature is only good for its key and until it expires', () => {
		const now = Date.parse('2031-01-08T08:00:00Z');
		const link = new URL(mediaUrl(storedUrlOf(KEY), now)!);
		const e = link.searchParams.get('e'), s = link.searchParams.get('s');
		expect(verifyMediaSignature(KEY, e, s, now)).toBe(true);
		expect(verifyMediaSignature('avatars/user-2/abcDEF123.jpg', e, s, now)).toBe(false);
		expect(verifyMediaSignature(KEY, e, s, now + 9 * 86_400_000)).toBe(false);
		expect(verifyMediaSignature(KEY, String(Number(e) + 86_400), s, now)).toBe(false);
		expect(verifyMediaSignature(KEY, e, null, now)).toBe(false);
	});

	test('only picture keys are recognised', () => {
		expect(keyOf(`${env.baseUrl}/media/../secrets.jpg`)).toBeNull();
		expect(keyOf(`${env.baseUrl}/media/chat/g1/file.pdf`)).toBeNull();
		expect(keyOf('https://evil.example/media/avatars/u/x.jpg')).toBeNull();
	});
});
