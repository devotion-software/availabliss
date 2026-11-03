import { beforeAll, describe, expect, test } from 'bun:test';
import { env } from './env';

// Without a .env (CI unit job) there is no key; give the module one before first use.
beforeAll(() => {
	if (!env.secretsKey) (env as { secretsKey: string | null }).secretsKey = 'unit-test-secrets-key';
});

const load = () => import('./crypto');

describe('encryptJson / decryptJson', () => {
	test('round trips JSON values', async () => {
		const { encryptJson, decryptJson } = await load();
		const value = { accessToken: 'a', refreshToken: 'r', expiresAt: 123, nested: [1, 'two', null] };
		const stored = encryptJson(value, 'calendar:1');
		expect(stored.split('.')).toHaveLength(3);
		expect(stored).not.toContain('accessToken');
		expect(decryptJson<typeof value>(stored, 'calendar:1')).toEqual(value);
	});

	test('uses a fresh IV each time', async () => {
		const { encryptJson } = await load();
		expect(encryptJson('same', 'c')).not.toBe(encryptJson('same', 'c'));
	});

	test('rejects a ciphertext moved to another context', async () => {
		const { encryptJson, decryptJson } = await load();
		const stored = encryptJson({ password: 'p' }, 'calendar:1');
		expect(() => decryptJson(stored, 'calendar:2')).toThrow();
	});

	test('rejects tampered data or tag', async () => {
		const { encryptJson, decryptJson } = await load();
		const [iv, tag, data] = encryptJson({ password: 'secret' }, 'ctx').split('.');
		const flip = (b64: string) => {
			const buf = Buffer.from(b64, 'base64url');
			buf[0] ^= 1;
			return buf.toString('base64url');
		};
		expect(() => decryptJson([iv, tag, flip(data)].join('.'), 'ctx')).toThrow();
		expect(() => decryptJson([iv, flip(tag), data].join('.'), 'ctx')).toThrow();
		expect(() => decryptJson([flip(iv), tag, data].join('.'), 'ctx')).toThrow();
	});
});
