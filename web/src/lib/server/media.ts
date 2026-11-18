/**
 * Profile pictures, group photos and event covers (and chat attachments): resized on the server
 * (sharp), stored privately in Cloudflare R2 (S3-compatible, signed with aws4fetch). Without R2
 * configured (local development), files go to `.uploads/` on disk. Pictures are served by
 * /media/[...key] behind signed links (see media-urls.ts); attachments by /api/v1/attachments.
 */
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { AwsClient } from 'aws4fetch';
import sharp from 'sharp';
import { dirname, join, resolve } from 'node:path';
import { env } from './env';
import { badRequest } from './http';
import { newToken } from './ids';
import { MEDIA_KEY, keyOf, storedUrlOf } from './media-urls';
import { log } from './log';

export { keyOf, mediaUrl } from './media-urls';

const LOCAL_DIR = resolve('.uploads');
/** Longest side of a stored picture: avatars and group photos are cropped small; covers show wide. */
export const IMAGE_SIZES = { avatar: 512, group: 512, cover: 1600 } as const;
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

const r2 = (() => {
	const { accountId, accessKeyId, secretAccessKey, bucket } = env.r2;
	if (!accountId || !accessKeyId || !secretAccessKey || !bucket) return null;
	const client = new AwsClient({ accessKeyId, secretAccessKey, service: 's3', region: 'auto' });
	const objectUrl = (key: string) => `https://${accountId}.r2.cloudflarestorage.com/${bucket}/${key}`;
	return { client, objectUrl };
})();

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

/** Stores a picture under a fresh key and returns where it lives (sign it with `mediaUrl` to send it). */
export async function putImage(kind: keyof typeof FOLDERS, ownerId: string, bytes: Uint8Array): Promise<string> {
	const key = `${FOLDERS[kind]}/${ownerId}/${newToken()}.jpg`;
	await putObject(key, bytes, 'image/jpeg');
	return storedUrlOf(key);
}

/** Writes bytes under `key` (R2, or `.uploads/` without it). */
export async function putObject(key: string, bytes: Uint8Array, contentType: string, cacheControl = 'private, max-age=31536000, immutable'): Promise<void> {
	if (r2) {
		const res = await r2.client.fetch(r2.objectUrl(key), {
			method: 'PUT',
			body: new Blob([bytes as Uint8Array<ArrayBuffer>], { type: contentType }),
			headers: { 'content-type': contentType, 'cache-control': cacheControl }
		});
		if (!res.ok) throw new Error(`R2 upload failed: ${res.status} ${await res.text()}`);
	} else {
		const path = join(LOCAL_DIR, key);
		await mkdir(dirname(path), { recursive: true });
		await writeFile(path, bytes);
	}
}

/**
 * Reads a stored object for serving it ourselves (private files such as chat attachments).
 * `range` is passed on to R2 (a 206 comes back); the local fallback always returns the whole file.
 */
export async function getObject(key: string, range: string | null = null): Promise<{ body: ReadableStream | Uint8Array; status: number; headers: Headers } | null> {
	if (r2) {
		const res = await r2.client.fetch(r2.objectUrl(key), { headers: range ? { range } : {} });
		if (res.status !== 200 && res.status !== 206) {
			await res.body?.cancel();
			return null;
		}
		const headers = new Headers();
		for (const name of ['content-length', 'content-range', 'accept-ranges', 'etag']) {
			const value = res.headers.get(name);
			if (value) headers.set(name, value);
		}
		return { body: res.body!, status: res.status, headers };
	}
	const bytes = await readFile(join(LOCAL_DIR, key)).catch(() => null);
	if (!bytes) return null;
	return { body: new Uint8Array(bytes), status: 200, headers: new Headers({ 'content-length': String(bytes.byteLength) }) };
}

/** Deletes stored objects by key (best effort). */
export async function deleteKeys(keys: string[]): Promise<void> {
	await Promise.all(
		keys.map(async (key) => {
			try {
				if (r2) await r2.client.fetch(r2.objectUrl(key), { method: 'DELETE' });
				else await rm(join(LOCAL_DIR, key), { force: true });
			} catch (err) {
				log.warn('media', 'delete failed', { key, err });
			}
		})
	);
}

/** Stores a user's picture under a fresh key and returns where it lives. */
export const putAvatar = (userId: string, bytes: Uint8Array) => putImage('avatar', userId, bytes);

/** Deletes one of our stored files (best effort; other URLs are left alone). */
export async function deleteStored(url: string | null): Promise<void> {
	const key = keyOf(url);
	if (key) await deleteKeys([key]);
}

/** A stored picture (R2, or `.uploads/` in development) for /media to serve, or null. */
export async function getPicture(key: string): Promise<{ body: ReadableStream | Uint8Array; headers: Headers } | null> {
	if (!MEDIA_KEY.test(key)) return null;
	const object = await getObject(key);
	return object && { body: object.body, headers: object.headers };
}
