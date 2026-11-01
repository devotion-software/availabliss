import { createPrivateKey, sign, type KeyObject } from 'node:crypto';

const b64url = (value: string | Buffer) => Buffer.from(value).toString('base64url');

/** Signs a compact JWT with ES256 (APNs) or RS256 (Google service accounts). */
export function signJwt(alg: 'ES256' | 'RS256', header: Record<string, string>, claims: Record<string, unknown>, pem: string): string {
	const key: KeyObject = createPrivateKey(pem);
	const input = `${b64url(JSON.stringify({ alg, typ: 'JWT', ...header }))}.${b64url(JSON.stringify(claims))}`;
	const signature =
		alg === 'ES256'
			? sign('sha256', Buffer.from(input), { key, dsaEncoding: 'ieee-p1363' })
			: sign('sha256', Buffer.from(input), key);
	return `${input}.${b64url(signature)}`;
}

/** Caches a token until shortly before it expires. */
export function cachedToken(ttlMs: number, create: () => Promise<string> | string): () => Promise<string> {
	let value: { token: string; expires: number } | null = null;
	return async () => {
		if (!value || value.expires < Date.now()) value = { token: await create(), expires: Date.now() + ttlMs };
		return value.token;
	};
}
