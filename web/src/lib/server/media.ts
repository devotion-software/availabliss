/**
 * Profile pictures: resized on the server (sharp), stored in Cloudflare R2 (S3-compatible,
 * signed with aws4fetch). Without R2 configured (local development), files go to `.uploads/`
 * on disk and are served by /media/[...key].
 */
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { AwsClient } from 'aws4fetch';
import sharp from 'sharp';
import { dirname, join, resolve } from 'node:path';
import { env } from './env';
import { badRequest } from './http';
import { newToken } from './ids';

const LOCAL_DIR = resolve('.uploads');
/** Longest side of a stored picture. The apps crop it into a circle. */
const MAX_SIDE = 512;
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

const r2 = (() => {
	const { accountId, accessKeyId, secretAccessKey, bucket, publicUrl } = env.r2;
	if (!accountId || !accessKeyId || !secretAccessKey || !bucket || !publicUrl) return null;
	const client = new AwsClient({ accessKeyId, secretAccessKey, service: 's3', region: 'auto' });
	const objectUrl = (key: string) => `https://${accountId}.r2.cloudflarestorage.com/${bucket}/${key}`;
	return { client, objectUrl, publicUrl };
})();

/** Where a stored file is served from. */
function publicUrlOf(key: string): string {
	return r2 ? `${r2.publicUrl}/${key}` : `${env.baseUrl}/media/${key}`;
}

/** The storage key behind one of our URLs, or null for anyone else's (e.g. a Google photo). */
export function keyOf(url: string | null): string | null {
	if (!url) return null;
	for (const base of [r2?.publicUrl, `${env.baseUrl}/media`]) {
		if (base && url.startsWith(`${base}/`)) return url.slice(base.length + 1);
	}
	return null;
}

/** Shrinks an uploaded image to fit 512×512 and re-encodes it as JPEG (dropping any metadata). */
export async function processAvatar(input: ArrayBuffer): Promise<Uint8Array> {
	if (input.byteLength === 0) throw badRequest('No image was sent');
	if (input.byteLength > MAX_UPLOAD_BYTES) throw badRequest('Images can be up to 10 MB');
	try {
		// rotate(): apply the EXIF orientation (phone photos), which re-encoding would otherwise drop.
		const out = await sharp(Buffer.from(input), { limitInputPixels: 50_000_000 })
			.rotate()
			.resize(MAX_SIDE, MAX_SIDE, { fit: 'inside', withoutEnlargement: true })
			.jpeg({ quality: 82, mozjpeg: true })
			.toBuffer();
		return new Uint8Array(out);
	} catch {
		throw badRequest("That doesn't look like a photo we can use. Try a JPEG, PNG, WebP or HEIC.");
	}
}

/** Stores a user's picture under a fresh key and returns its public URL. */
export async function putAvatar(userId: string, bytes: Uint8Array): Promise<string> {
	const key = `avatars/${userId}/${newToken()}.jpg`;
	if (r2) {
		const res = await r2.client.fetch(r2.objectUrl(key), {
			method: 'PUT',
			body: new Blob([bytes as Uint8Array<ArrayBuffer>], { type: "image/jpeg" }),
			headers: { 'content-type': 'image/jpeg', 'cache-control': 'public, max-age=31536000, immutable' }
		});
		if (!res.ok) throw new Error(`R2 upload failed: ${res.status} ${await res.text()}`);
	} else {
		const path = join(LOCAL_DIR, key);
		await mkdir(dirname(path), { recursive: true });
		await writeFile(path, bytes);
	}
	return publicUrlOf(key);
}

/** Deletes one of our stored files (best effort; other URLs are left alone). */
export async function deleteStored(url: string | null): Promise<void> {
	const key = keyOf(url);
	if (!key) return;
	try {
		if (r2) await r2.client.fetch(r2.objectUrl(key), { method: 'DELETE' });
		else await rm(join(LOCAL_DIR, key), { force: true });
	} catch (err) {
		console.warn('[media] delete failed', key, err);
	}
}

/** A locally stored file's bytes (dev without R2), or null. */
export async function localFile(key: string): Promise<Buffer | null> {
	if (r2 || !/^avatars\/[\w-]+\/[\w-]+\.jpg$/.test(key)) return null;
	return readFile(join(LOCAL_DIR, key)).catch(() => null);
}
