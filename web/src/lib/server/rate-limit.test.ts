import { describe, expect, test } from 'bun:test';
import { MemoryStore } from './redis';
import { GENERAL, OTP_PER_EMAIL, RULES, checkOtpEmail, checkRateLimit, matchRule, tooManyRequests } from './rate-limit';

const signedIn = { userId: 'u1', ip: '1.1.1.1' };
const anonymous = { userId: null, ip: '2.2.2.2' };

async function hitTimes(n: number, fn: () => Promise<{ allowed: boolean }>) {
	let last = { allowed: true } as { allowed: boolean };
	for (let i = 0; i < n; i++) last = await fn();
	return last;
}

describe('matchRule', () => {
	test('matches method and path', () => {
		expect(matchRule('POST', '/api/v1/calendars/abc/sync')?.name).toBe('calendar-sync');
		expect(matchRule('GET', '/api/v1/calendars/abc/sync')).toBeUndefined();
		expect(matchRule('GET', '/api/v1/invites/tok')?.name).toBe('invite');
		expect(matchRule('POST', '/api/v1/invites/tok/accept')?.name).toBe('invite');
		expect(matchRule('GET', '/api/v1/groups/g1/availability/day')?.name).toBe('availability');
		expect(matchRule('POST', '/api/v1/groups')?.name).toBe('group-create');
		expect(matchRule('PATCH', '/api/v1/groups/g1')).toBeUndefined();
	});

	test('every rule is reachable (no earlier rule shadows it)', () => {
		const samples: Record<string, [string, string]> = {
			'native-exchange': ['POST', '/api/v1/auth/native/exchange'],
			invite: ['GET', '/api/v1/invites/x'],
			live: ['GET', '/api/v1/live'],
			'calendar-sync': ['POST', '/api/v1/calendars/c/sync'],
			'calendar-connect': ['POST', '/api/v1/calendars/caldav'],
			'device-busy': ['PUT', '/api/v1/calendars/device'],
			'group-create': ['POST', '/api/v1/groups'],
			'invite-create': ['POST', '/api/v1/groups/g/invites'],
			message: ['POST', '/api/v1/groups/g/messages'],
			availability: ['GET', '/api/v1/groups/g/availability'],
			preview: ['POST', '/api/v1/requirements/preview'],
			'ai-planner': ['POST', '/api/v1/groups/g/planner'],
			'region-detect': ['GET', '/api/v1/regions/detect']
		};
		for (const rule of RULES) {
			const sample = samples[rule.name];
			expect(sample).toBeDefined();
			expect(matchRule(...sample)?.name).toBe(rule.name);
		}
	});
});

describe('checkRateLimit', () => {
	test('a specific rule blocks after its max and reports retry-after', async () => {
		const store = new MemoryStore();
		const sync = () => checkRateLimit(store, 'POST', '/api/v1/calendars/c/sync', signedIn);
		expect((await hitTimes(6, sync)).allowed).toBe(true);
		const blocked = await sync();
		expect(blocked).toEqual({ allowed: false, retryAfter: 60, bucket: 'calendar-sync' });
	});

	test('limits are per user', async () => {
		const store = new MemoryStore();
		await hitTimes(7, () => checkRateLimit(store, 'POST', '/api/v1/calendars/c/sync', signedIn));
		expect((await checkRateLimit(store, 'POST', '/api/v1/calendars/c/sync', { userId: 'u2', ip: '1.1.1.1' })).allowed).toBe(true);
	});

	test('per-IP rules ignore the user', async () => {
		const store = new MemoryStore();
		await hitTimes(10, () => checkRateLimit(store, 'POST', '/api/v1/auth/native/exchange', { userId: null, ip: '9.9.9.9' }));
		const otherUserSameIp = await checkRateLimit(store, 'POST', '/api/v1/auth/native/exchange', { userId: 'u3', ip: '9.9.9.9' });
		expect(otherUserSameIp.allowed).toBe(false);
	});

	test('general budgets: writes are tighter than reads, anonymous by IP', async () => {
		const store = new MemoryStore();
		expect((await hitTimes(GENERAL.write.max, () => checkRateLimit(store, 'PATCH', '/api/v1/me', signedIn))).allowed).toBe(true);
		expect((await checkRateLimit(store, 'PATCH', '/api/v1/me', signedIn)).allowed).toBe(false);
		// Reads have their own budget.
		expect((await checkRateLimit(store, 'GET', '/api/v1/me', signedIn)).allowed).toBe(true);

		expect((await hitTimes(GENERAL.anonymous.max, () => checkRateLimit(store, 'GET', '/api/v1/health', anonymous))).allowed).toBe(true);
		expect((await checkRateLimit(store, 'GET', '/api/v1/health', anonymous)).allowed).toBe(false);
	});

	test('windows reset', async () => {
		let now = 0;
		const store = new MemoryStore(() => now);
		await hitTimes(7, () => checkRateLimit(store, 'POST', '/api/v1/calendars/c/sync', signedIn));
		now = 61_000;
		expect((await checkRateLimit(store, 'POST', '/api/v1/calendars/c/sync', signedIn)).allowed).toBe(true);
	});
});

test('OTP emails are limited per address, case-insensitively', async () => {
	const store = new MemoryStore();
	expect((await hitTimes(OTP_PER_EMAIL.max, () => checkOtpEmail(store, 'Sam@Example.com'))).allowed).toBe(true);
	expect((await checkOtpEmail(store, ' sam@example.com')).allowed).toBe(false);
	expect((await checkOtpEmail(store, 'priya@example.com')).allowed).toBe(true);
});

test('429 response carries the API error shape and Retry-After', async () => {
	const res = tooManyRequests(90);
	expect(res.status).toBe(429);
	expect(res.headers.get('retry-after')).toBe('90');
	expect(await res.json()).toEqual({ message: 'Too many requests. Try again in 2 minutes.', code: 'rate_limited' });
	expect((await tooManyRequests(60).json()).message).toBe('Too many requests. Try again in 1 minute.');
	expect((await tooManyRequests(1).json()).message).toBe('Too many requests. Try again in 1 second.');
});
