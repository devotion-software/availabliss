import { describe, expect, spyOn, test } from 'bun:test';
import { consumeState, decodeState, encodeState, parkPendingLink, safeRedirect, takePendingLink } from './oauth';
import { env, signingSecret } from '../env';

describe('OAuth state', () => {
	test('round-trips and rejects tampering', () => {
		const raw = encodeState({ userId: 'u1', provider: 'google', redirect: '/app/calendars' });
		expect(decodeState(raw)).toMatchObject({ userId: 'u1', provider: 'google', redirect: '/app/calendars' });
		const [payload, sig] = raw.split('.');
		const forged = Buffer.from(JSON.stringify({ ...JSON.parse(Buffer.from(payload, 'base64url').toString()), userId: 'u2' })).toString('base64url');
		expect(() => decodeState(`${forged}.${sig}`)).toThrow('Invalid sign-in state');
	});

	test('rejects other signatures, garbage and missing states', () => {
		const raw = encodeState({ userId: 'u1', provider: 'google', redirect: '/app/calendars' });
		const [payload, sig] = raw.split('.');
		expect(() => decodeState(`${payload}.${sig.slice(1)}`)).toThrow('Invalid sign-in state'); // wrong length
		expect(() => decodeState(`${payload}.${'A'.repeat(sig.length)}`)).toThrow('Invalid sign-in state');
		expect(() => decodeState(payload)).toThrow('Invalid sign-in state');
		expect(() => decodeState('')).toThrow('Invalid sign-in state');
		expect(() => decodeState(null)).toThrow('Invalid sign-in state');
	});

	test('expires after 10 minutes', () => {
		const raw = encodeState({ userId: 'u1', provider: 'microsoft', redirect: '/app/calendars' });
		const later = spyOn(Date, 'now').mockReturnValue(Date.now() + 11 * 60_000);
		try {
			expect(() => decodeState(raw)).toThrow('expired');
		} finally {
			later.mockRestore();
		}
	});

	test('a state finishes at most one connection', async () => {
		const state = decodeState(encodeState({ userId: 'u1', provider: 'google', redirect: '/app/calendars' }));
		await consumeState(state);
		await expect(consumeState(state)).rejects.toThrow('already used');
	});
});

describe('native hand-off', () => {
	test('only the user who started the flow can complete it, once', async () => {
		const handoff = await parkPendingLink({ userId: 'u1', provider: 'microsoft', code: 'provider-code' });
		expect(await takePendingLink(handoff, 'u1', 'microsoft')).toBe('provider-code');
		await expect(takePendingLink(handoff, 'u1', 'microsoft')).rejects.toThrow('expired');
	});

	test('another user is refused, and the code is spent', async () => {
		const handoff = await parkPendingLink({ userId: 'attacker', provider: 'google', code: 'victim-code' });
		await expect(takePendingLink(handoff, 'victim', 'google')).rejects.toMatchObject({ status: 403 });
		await expect(takePendingLink(handoff, 'attacker', 'google')).rejects.toThrow('expired');
	});

	test('the provider must match', async () => {
		const handoff = await parkPendingLink({ userId: 'u1', provider: 'google', code: 'c' });
		await expect(takePendingLink(handoff, 'u1', 'microsoft')).rejects.toMatchObject({ status: 403 });
	});
});

describe('safeRedirect', () => {
	test('keeps app paths and the app scheme, falls back otherwise', () => {
		expect(safeRedirect('/app/settings')).toBe('/app/settings');
		expect(safeRedirect('availabliss://calendars?complete=1')).toBe('availabliss://calendars?complete=1');
		for (const bad of ['https://evil.example/x', '//evil.example', '/\\evil.example', 'javascript:alert(1)', 'availabliss://x\ny', 'availabliss://a\\b', null, '']) {
			expect(safeRedirect(bad)).toBe('/app/calendars');
		}
	});
});

describe('signing secret', () => {
	test('production refuses the development fallback', () => {
		const saved = { secret: env.authSecret, nodeEnv: process.env.NODE_ENV };
		const mutable = env as { authSecret: string | null };
		try {
			mutable.authSecret = null;
			process.env.NODE_ENV = 'development';
			expect(signingSecret()).toBe('dev');
			process.env.NODE_ENV = 'production';
			expect(() => signingSecret()).toThrow('BETTER_AUTH_SECRET');
			expect(() => encodeState({ userId: 'u1', provider: 'google', redirect: '/app' })).toThrow('BETTER_AUTH_SECRET');
			mutable.authSecret = 'configured';
			expect(signingSecret()).toBe('configured');
		} finally {
			mutable.authSecret = saved.secret;
			process.env.NODE_ENV = saved.nodeEnv;
		}
	});
});
