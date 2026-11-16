/**
 * Profile pictures, group photos and event covers: resized on the server (sharp), stored in Cloudflare R2 (S3-compatible,
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
/** Longest side of a stored picture: avatars and group photos are cropped small; covers show wide. */
export const IMAGE_SIZES = { avatar: 512, group: 512, cover: 1600 } as const;
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

/** Shrinks an uploaded image to fit `maxSide` and re-encodes it as JPEG (dropping any metadata). */
export async function processImage(input: ArrayBuffer, maxSide: number = IMAGE_SIZES.avatar): Promise<Uint8Array> {
	if (input.byteLength === 0) throw badRequest('No image was sent');
	if (input.byteLength > MAX_UPLOAD_BYTES) throw badRequest('Images can be up to 10 MB');
	try {
		// rotate(): apply the EXIF orientation (phone photos), which re-encoding would otherwise drop.
		const out = await sharp(Buffer.from(input), { limitInputPixels: 50_000_000 })
			.rotate()
			.resize(maxSide, maxSide, { fit: 'inside', withoutEnlargement: true })
			.jpeg({ quality: 82, mozjpeg: true })
			.toBuffer();
		return new Uint8Array(out);
	} catch {
		throw badRequest("That doesn't look like a photo we can use. Try a JPEG, PNG, WebP or HEIC.");
	}
}

export const processAvatar = (input: ArrayBuffer) => processImage(input, IMAGE_SIZES.avatar);

/** Where each kind of picture is kept: `<folder>/<owner id>/<token>.jpg`. */
const FOLDERS = { avatar: 'avatars', group: 'groups', cover: 'events' } as const;

/** Stores a picture under a fresh key and returns its public URL. */
export async function putImage(kind: keyof typeof FOLDERS, ownerId: string, bytes: Uint8Array): Promise<string> {
	const key = `${FOLDERS[kind]}/${ownerId}/${newToken()}.jpg`;
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

/** Stores a user's picture under a fresh key and returns its public URL. */
export const putAvatar = (userId: string, bytes: Uint8Array) => putImage('avatar', userId, bytes);

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
	if (r2 || !/^(avatars|groups|events)\/[\w-]+\/[\w-]+\.jpg$/.test(key)) return null;
	return readFile(join(LOCAL_DIR, key)).catch(() => null);
}
