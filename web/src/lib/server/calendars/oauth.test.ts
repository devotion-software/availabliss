import { describe, expect, test } from 'bun:test';
import { consumeState, decodeState, encodeState, parkPendingLink, takePendingLink } from './oauth';

describe('OAuth state', () => {
	test('round-trips and rejects tampering', () => {
		const raw = encodeState({ userId: 'u1', provider: 'google', redirect: '/app/calendars' });
		expect(decodeState(raw)).toMatchObject({ userId: 'u1', provider: 'google', redirect: '/app/calendars' });
		const [payload, sig] = raw.split('.');
		const forged = Buffer.from(JSON.stringify({ ...JSON.parse(Buffer.from(payload, 'base64url').toString()), userId: 'u2' })).toString('base64url');
		expect(() => decodeState(`${forged}.${sig}`)).toThrow('Invalid sign-in state');
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
