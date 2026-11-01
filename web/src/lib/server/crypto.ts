import { createCipheriv, createDecipheriv, hkdfSync, randomBytes } from 'node:crypto';
import { env } from './env';

/*
 * AES-256-GCM for secrets at rest (calendar OAuth tokens, CalDAV passwords).
 * The key is derived from SECRETS_KEY; changing it makes stored secrets unreadable.
 * `context` is bound in as associated data so a ciphertext copied to another row fails.
 */
let cachedKey: Buffer | null = null;
function key(): Buffer {
	if (cachedKey) return cachedKey;
	if (!env.secretsKey) throw new Error('SECRETS_KEY (or BETTER_AUTH_SECRET) must be set');
	cachedKey = Buffer.from(hkdfSync('sha256', env.secretsKey, 'availabliss', 'secrets:v1', 32));
	return cachedKey;
}

export function encryptJson(value: unknown, context: string): string {
	const iv = randomBytes(12);
	const cipher = createCipheriv('aes-256-gcm', key(), iv);
	cipher.setAAD(Buffer.from(context));
	const data = Buffer.concat([cipher.update(JSON.stringify(value), 'utf8'), cipher.final()]);
	return [iv, cipher.getAuthTag(), data].map((b) => b.toString('base64url')).join('.');
}

export function decryptJson<T>(stored: string, context: string): T {
	const [iv, tag, data] = stored.split('.').map((p) => Buffer.from(p, 'base64url'));
	const decipher = createDecipheriv('aes-256-gcm', key(), iv);
	decipher.setAAD(Buffer.from(context));
	decipher.setAuthTag(tag);
	return JSON.parse(Buffer.concat([decipher.update(data), decipher.final()]).toString('utf8')) as T;
}
