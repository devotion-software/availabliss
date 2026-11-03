import { describe, expect, test } from 'bun:test';
import { challengeFor, decodeFlow, encodeFlow, isChallenge, issueCode, redeemCode, sessionFromFlow } from './native-auth';

const verifier = 'a'.repeat(43);

describe('native sign-in binding', () => {
	test('S256 challenge: base64url(SHA-256(verifier)), checked against openssl', () => {
		expect(challengeFor('dBjftJeZ4CVP-mJ92K9gYqF5yI1gG8ZoMAvHOn0vR3U')).toBe('V3SuLHIK7yk4RyrjZ3sQ8mzmOovJLsWYWwmh1P1rX4M');
		expect(isChallenge(challengeFor(verifier))).toBe(true);
		expect(isChallenge('short')).toBe(false);
		expect(isChallenge(null)).toBe(false);
	});

	test('the flow cookie is signed and expires', () => {
		const now = Date.now();
		const raw = encodeFlow(challengeFor(verifier), now);
		expect(decodeFlow(raw, now)).toEqual({ challenge: challengeFor(verifier), startedAt: now });
		expect(decodeFlow(raw, now + 11 * 60_000)).toBeNull();
		expect(decodeFlow(`${raw.split('.')[0]}.forged`, now)).toBeNull();
		expect(decodeFlow(undefined, now)).toBeNull();
	});

	test('only sessions created by the flow count', () => {
		const flow = { challenge: challengeFor(verifier), startedAt: Date.now() };
		expect(sessionFromFlow(flow, new Date(flow.startedAt + 30_000))).toBe(true);
		expect(sessionFromFlow(flow, new Date(flow.startedAt - 60 * 60_000))).toBe(false);
	});

	test('a code needs the verifier and is single use', async () => {
		const code = await issueCode('session-token', challengeFor(verifier));
		expect(await redeemCode(code, verifier)).toBe('session-token');
		await expect(redeemCode(code, verifier)).rejects.toThrow('expired');
	});

	test('a wrong verifier is refused and burns the code', async () => {
		const code = await issueCode('session-token', challengeFor(verifier));
		await expect(redeemCode(code, 'b'.repeat(43))).rejects.toThrow("wasn't started by this app");
		await expect(redeemCode(code, verifier)).rejects.toThrow('expired');
	});
});
