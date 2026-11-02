import { describe, expect, test } from 'bun:test';
import { isForbiddenCrossSite, selfOrigins } from './csrf';

const SELF = 'https://availabliss.app';
const allowed = selfOrigins(SELF, ['http://10.0.2.2:5190']);

function check(method: string, path: string, headers: Record<string, string> = {}) {
	const url = new URL(path, SELF);
	return isForbiddenCrossSite(new Request(url, { method, headers }), url, allowed);
}

describe('isForbiddenCrossSite', () => {
	test('reads are never blocked', () => {
		expect(check('GET', '/api/v1/me', { origin: 'https://evil.example' })).toBe(false);
	});

	test('native apps: bearer requests without Origin pass, body or not', () => {
		expect(check('POST', '/api/v1/invites/abc/accept', { authorization: 'Bearer t' })).toBe(false);
		expect(check('DELETE', '/api/v1/blocks/1', { authorization: 'bearer t' })).toBe(false);
	});

	test('same-origin and trusted origins pass', () => {
		expect(check('POST', '/api/v1/groups', { origin: SELF })).toBe(false);
		expect(check('POST', '/api/v1/groups', { origin: 'http://10.0.2.2:5190' })).toBe(false);
	});

	test('cross-site cookie requests are blocked', () => {
		expect(check('POST', '/api/v1/invites/abc/accept', { origin: 'https://evil.example' })).toBe(true);
		expect(check('DELETE', '/api/v1/blocks/1', { 'sec-fetch-site': 'cross-site' })).toBe(true);
	});

	test('Better Auth routes are left to Better Auth (Apple form_post callback)', () => {
		expect(check('POST', '/api/auth/callback/apple', { origin: 'https://appleid.apple.com' })).toBe(false);
	});

	test('non-browser clients without Origin pass', () => {
		expect(check('POST', '/api/v1/auth/native/exchange')).toBe(false);
	});
});

test('selfOrigins normalises the base URL and tolerates junk', () => {
	expect(selfOrigins('https://availabliss.app/path', [])).toEqual(['https://availabliss.app']);
	expect(selfOrigins('not a url', ['https://x.test'])).toEqual(['https://x.test']);
});
