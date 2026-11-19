/**
 * Photos, GIFs and files in group chat. Uploaded one at a time (POST
 * /groups/:id/attachments), then sent with a message that names their ids. Unlike
 * profile pictures they are private: the bytes are kept under unguessable keys and only
 * served by /api/v1/attachments/:id with a signature that expires (links handed to group
 * members inside messages), or to a signed-in member without one.
 */
import { createHmac, hkdfSync, timingSafeEqual } from 'node:crypto';
import { and, asc, desc, eq, gt, inArray, isNull, lt, or } from 'drizzle-orm';
import sharp from 'sharp';
import type { Attachment, AttachmentKind, GroupMediaItem, WalletPass } from '#lib/types';
import { db } from './db';
import { messageAttachments, messages } from './db/schema';
import { env } from './env';
import { ApiError, badRequest, notFound } from './http';
import { newId, newToken } from './ids';
import { deleteKeys, getObject, putObject } from './media';
import { requireMember, roleIn } from './groups';
import { passTitle, readWalletPass } from './wallet-pass';
import { videoDurationSeconds } from './video-duration';
import { isPro } from './billing';

type AttachmentRow = typeof messageAttachments.$inferSelect;

export const MAX_IMAGE_BYTES = 15 * 1024 * 1024;
export const MAX_FILE_BYTES = 25 * 1024 * 1024;
/** Videos: up to 3 minutes and 100 MB, or with Pro 10 minutes and 300 MB (after the apps shrink them to 720p). */
export const VIDEO_LIMITS = {
	free: { seconds: 3 * 60, bytes: 100 * 1024 * 1024 },
	pro: { seconds: 10 * 60, bytes: 300 * 1024 * 1024 }
} as const;
export const MAX_VIDEO_BYTES = VIDEO_LIMITS.pro.bytes;
/** Most attachments one message can carry. */
export const MAX_ATTACHMENTS = 10;
/** Uploads never sent with a message are deleted after this long. */
export const PENDING_TTL_MS = 24 * 60 * 60 * 1000;

/** Longest side of a stored photo, and of its preview in the chat. */
const PHOTO_SIDE = 2560;
const THUMB_SIDE = 720;

/** Photos (converted to JPEG, or PNG when they have transparency); an animated GIF is kept as it is. */
const IMAGE_TYPES: Record<string, string> = {
	jpg: 'image/jpeg',
	jpeg: 'image/jpeg',
	png: 'image/png',
	webp: 'image/webp',
	gif: 'image/gif',
	heic: 'image/heic',
	heif: 'image/heif'
};

/**
 * Files people can send, by extension: documents, spreadsheets, slides, archives and
 * common audio / video. Anything else (programs, scripts, web pages, SVG) is refused.
 */
export const FILE_TYPES: Record<string, string> = {
	pdf: 'application/pdf',
	txt: 'text/plain',
	md: 'text/markdown',
	csv: 'text/csv',
	rtf: 'application/rtf',
	ics: 'text/calendar',
	vcf: 'text/vcard',
	doc: 'application/msword',
	docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
	xls: 'application/vnd.ms-excel',
	xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
	ppt: 'application/vnd.ms-powerpoint',
	pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
	odt: 'application/vnd.oasis.opendocument.text',
	ods: 'application/vnd.oasis.opendocument.spreadsheet',
	odp: 'application/vnd.oasis.opendocument.presentation',
	pages: 'application/vnd.apple.pages',
	numbers: 'application/vnd.apple.numbers',
	key: 'application/vnd.apple.keynote',
	epub: 'application/epub+zip',
	zip: 'application/zip',
	mp3: 'audio/mpeg',
	m4a: 'audio/mp4',
	wav: 'audio/wav',
	// Apple Wallet passes: shown as a card (kind 'pass') when their pass.json can be read.
	pkpass: 'application/vnd.apple.pkpass',
	pkpasses: 'application/vnd.apple.pkpasses'
};

/** Videos that play in the chat (MP4 / QuickTime, whose length can be read from the header). */
const VIDEO_TYPES: Record<string, string> = {
	mp4: 'video/mp4',
	m4v: 'video/x-m4v',
	mov: 'video/quicktime'
};

const PASS_MIMES = new Set(['application/vnd.apple.pkpass', 'application/vnd.apple.pkpasses']);

// ─── Signed links ─────────────────────────────────────────────────────────

export type Variant = 'file' | 'thumb';

const DAY_SECONDS = 86_400;
/** Links last 7–8 days and stay the same all day, so apps can cache what they load. */
const LINK_DAYS = 8;

let signingKey: Buffer | null = null;
function sign(id: string, variant: Variant, expires: number): string {
	if (!signingKey) {
		if (!env.secretsKey) throw new Error('SECRETS_KEY (or BETTER_AUTH_SECRET) must be set');
		signingKey = Buffer.from(hkdfSync('sha256', env.secretsKey, 'availabliss', 'attachments:v1', 32));
	}
	return createHmac('sha256', signingKey).update(`${id}:${variant}:${expires}`).digest('base64url').slice(0, 32);
}

export function signedUrl(id: string, variant: Variant, now = Date.now()): string {
	const expires = (Math.floor(now / 1000 / DAY_SECONDS) + LINK_DAYS) * DAY_SECONDS;
	const path = variant === 'thumb' ? `${id}/thumb` : id;
	return `${env.baseUrl}/api/v1/attachments/${path}?e=${expires}&s=${sign(id, variant, expires)}`;
}

/** Whether `e` / `s` from a link are a valid, unexpired signature for this attachment. */
export function verifySignature(id: string, variant: Variant, e: string | null, s: string | null, now = Date.now()): boolean {
	const expires = Number(e);
	if (!s || !Number.isInteger(expires) || expires * 1000 < now) return false;
	const expected = Buffer.from(sign(id, variant, expires));
	const given = Buffer.from(s);
	return given.length === expected.length && timingSafeEqual(given, expected);
}

// ─── Reading ──────────────────────────────────────────────────────────────

function toAttachment(row: AttachmentRow): Attachment {
	return {
		id: row.id,
		kind: row.kind,
		name: row.name,
		mime: row.mime,
		size: row.size,
		width: row.width,
		height: row.height,
		url: signedUrl(row.id, 'file'),
		thumbnailUrl: row.thumbKey ? signedUrl(row.id, 'thumb') : null,
		pass: row.kind === 'pass' ? row.pass : null,
		duration: row.kind === 'video' ? row.duration : null
	};
}

/** The attachments of each message, in order. */
export async function attachmentsFor(messageIds: string[]): Promise<Map<string, Attachment[]>> {
	const result = new Map<string, Attachment[]>();
	if (!messageIds.length) return result;
	const rows = await db
		.select()
		.from(messageAttachments)
		.where(inArray(messageAttachments.messageId, [...new Set(messageIds)]))
		.orderBy(asc(messageAttachments.position));
	for (const row of rows) {
		const list = result.get(row.messageId!) ?? [];
		list.push(toAttachment(row));
		result.set(row.messageId!, list);
	}
	return result;
}

/** What each tab of a chat's media page shows. */
export const MEDIA_KINDS = { media: ['image', 'gif', 'video'], files: ['file'], passes: ['pass'] } as const satisfies Record<
	string,
	readonly AttachmentKind[]
>;

/**
 * Everything sent in a group's chat, newest first (in message order within a message): its photos,
 * GIFs and videos (`media`), files (`files`) or Wallet passes (`passes`). Pass the last one's id as
 * `before` for the page after. Attachments of deleted messages are gone for good, so never listed.
 * The first page also says whether the chat has any passes, so the apps only show a Passes tab then.
 */
export async function listGroupMedia(
	groupId: string,
	userId: string,
	kind: keyof typeof MEDIA_KINDS,
	before: string | null,
	limit: number
): Promise<{ items: GroupMediaItem[]; hasMore: boolean; hasPasses?: boolean }> {
	await requireMember(groupId, userId);
	let after: { seq: number; position: number } | null = null;
	if (before) {
		const [anchor] = await db
			.select({ seq: messages.seq, position: messageAttachments.position })
			.from(messageAttachments)
			.innerJoin(messages, eq(messages.id, messageAttachments.messageId))
			.where(and(eq(messageAttachments.id, before), eq(messageAttachments.groupId, groupId)));
		if (!anchor) throw notFound('Attachment not found');
		after = anchor;
	}
	const rows = await db
		.select({ attachment: messageAttachments, messageId: messages.id, authorId: messages.authorId, createdAt: messages.createdAt })
		.from(messageAttachments)
		.innerJoin(messages, eq(messages.id, messageAttachments.messageId))
		.where(
			and(
				eq(messageAttachments.groupId, groupId),
				inArray(messageAttachments.kind, [...MEDIA_KINDS[kind]]),
				isNull(messages.deletedAt),
				after ? or(lt(messages.seq, after.seq), and(eq(messages.seq, after.seq), gt(messageAttachments.position, after.position))) : undefined
			)
		)
		.orderBy(desc(messages.seq), asc(messageAttachments.position))
		.limit(limit + 1);
	return {
		items: rows.slice(0, limit).map((r) => ({
			messageId: r.messageId,
			authorId: r.authorId,
			createdAt: r.createdAt.toISOString(),
			attachment: toAttachment(r.attachment)
		})),
		hasMore: rows.length > limit,
		...(!before && { hasPasses: kind === 'passes' ? rows.length > 0 : await hasPasses(groupId) })
	};
}

/** Whether a group's chat has any Wallet passes (in messages that aren't deleted). */
async function hasPasses(groupId: string): Promise<boolean> {
	const [row] = await db
		.select({ id: messageAttachments.id })
		.from(messageAttachments)
		.innerJoin(messages, eq(messages.id, messageAttachments.messageId))
		.where(and(eq(messageAttachments.groupId, groupId), eq(messageAttachments.kind, 'pass'), isNull(messages.deletedAt)))
		.limit(1);
	return row !== undefined;
}

/**
 * A one-line stand-in for attachments without a caption: the body older apps show, the
 * quote above a reply and the push text. "📷 Photo", "GIF", "📷 3 photos", "📎 Plan.pdf",
 * "📎 2 files", "🎟️ Concert ticket", "🎟️ 2 passes".
 */
export function attachmentSummary(list: (Pick<Attachment, 'kind' | 'name'> & { pass?: WalletPass | null })[]): string {
	if (!list.length) return '';
	const files = list.filter((a) => a.kind === 'file' || a.kind === 'pass');
	const passes = list.filter((a) => a.kind === 'pass');
	const videos = list.filter((a) => a.kind === 'video');
	if (list.length === 1) {
		const [only] = list;
		if (only.kind === 'pass') return `🎟️ ${passTitle(only.pass ?? null)}`;
		if (only.kind === 'video') return '🎥 Video';
		return only.kind === 'gif' ? 'GIF' : only.kind === 'image' ? '📷 Photo' : `📎 ${only.name}`;
	}
	if (videos.length === list.length) return `🎥 ${list.length} videos`;
	if (!files.length) return videos.length ? `📷 ${list.length} photos and videos` : `📷 ${list.length} photos`;
	if (passes.length === list.length) return `🎟️ ${list.length} passes`;
	return files.length === list.length ? `📎 ${list.length} files` : `📎 ${list.length} attachments`;
}

/**
 * Streams an attachment to whoever holds a valid link, or to a signed-in member of its group.
 * Photos and GIFs show inline; files always download (`?download=1` downloads a photo too).
 */
export async function serveAttachment(id: string, variant: Variant, url: URL, userId: string | null, range: string | null): Promise<Response> {
	const row = await db.query.messageAttachments.findFirst({ where: eq(messageAttachments.id, id) });
	const key = variant === 'thumb' ? row?.thumbKey : row?.storageKey;
	if (!row || !key) throw notFound('Attachment not found');
	const signed = verifySignature(id, variant, url.searchParams.get('e'), url.searchParams.get('s'));
	// Without a valid link: only a member (pending uploads: only the uploader).
	const allowed = signed || (userId !== null && (row.messageId ? (await roleIn(row.groupId, userId)) !== null : row.uploaderId === userId));
	if (!allowed) throw notFound('Attachment not found');

	const object = await getObject(key, range);
	if (!object) throw notFound('Attachment not found');
	// A pass is served as one so Safari offers to add it to Wallet (other browsers download it).
	const inline = row.kind !== 'file' && url.searchParams.get('download') !== '1';
	const mime = variant === 'thumb' ? (key.endsWith('.png') ? 'image/png' : 'image/jpeg') : row.mime;
	const headers = object.headers;
	headers.set('content-type', mime);
	headers.set('content-disposition', `${inline ? 'inline' : 'attachment'}; filename*=UTF-8''${encodeURIComponent(row.name)}`);
	headers.set('cache-control', 'private, max-age=604800, immutable');
	headers.set('x-content-type-options', 'nosniff');
	// Never run as a page, whatever the bytes are. (Not sandboxed for a pass: Safari hands it to Wallet.)
	headers.set('content-security-policy', row.kind === 'pass' && variant === 'file' ? "default-src 'none'" : "default-src 'none'; sandbox");
	return new Response(object.body as BodyInit, { status: object.status, headers });
}

// ─── Uploading ────────────────────────────────────────────────────────────

const extensionOf = (name: string) => (name.includes('.') ? name.split('.').pop()!.toLowerCase() : '');

/** A file name that is safe to store and show: no folders or control characters, at most 120 characters. */
export function cleanName(raw: string): string {
	// eslint-disable-next-line no-control-regex
	const base = (raw.split(/[/\\]/).pop() ?? '').replace(/[\u0000-\u001f\u007f]/g, '').trim();
	const chars = [...base];
	if (chars.length <= 120) return base || 'file';
	const ext = extensionOf(base);
	const keep = ext && ext.length < 12 ? 120 - ext.length - 1 : 120;
	return ext && ext.length < 12 ? `${chars.slice(0, keep).join('').trimEnd()}.${ext}` : chars.slice(0, keep).join('');
}

const withExtension = (name: string, ext: string) => `${name.includes('.') ? name.slice(0, name.lastIndexOf('.')) : name}.${ext}`;

interface Prepared {
	kind: AttachmentKind;
	name: string;
	mime: string;
	bytes: Uint8Array;
	width: number | null;
	height: number | null;
	thumb: Uint8Array | null;
	ext: string;
	pass?: WalletPass | null;
	duration?: number | null;
}

/**
 * A video: kept as sent (the apps shrink it to 720p first), as long as its length can be read and is
 * within the plan's limits. The app's poster (a frame from it) becomes its preview.
 */
async function prepareVideo(
	file: File,
	name: string,
	ext: string,
	poster: File | null,
	limit: { seconds: number; bytes: number }
): Promise<Prepared> {
	const minutes = limit.seconds / 60;
	const megabytes = limit.bytes / 1024 / 1024;
	const tooBig = `Videos can be up to ${minutes} minutes and ${megabytes} MB${limit === VIDEO_LIMITS.free ? ` (${VIDEO_LIMITS.pro.seconds / 60} minutes with Pro)` : ''}`;
	if (file.size > limit.bytes) throw badRequest(tooBig);
	const bytes = new Uint8Array(await file.arrayBuffer());
	const seconds = videoDurationSeconds(bytes);
	if (seconds === null) throw badRequest("That video couldn't be read. Try an MP4 or MOV.");
	// A second's grace for rounding in the app's own check.
	if (seconds > limit.seconds + 1) throw badRequest(tooBig);
	let thumb: Uint8Array | null = null;
	let width: number | null = null;
	let height: number | null = null;
	if (poster && poster.size > 0 && poster.size <= MAX_IMAGE_BYTES) {
		try {
			const out = await sharp(Buffer.from(await poster.arrayBuffer()), { limitInputPixels: 100_000_000 })
				.rotate()
				.resize(THUMB_SIDE, THUMB_SIDE, { fit: 'inside', withoutEnlargement: true })
				.jpeg({ quality: 80, mozjpeg: true })
				.toBuffer({ resolveWithObject: true });
			thumb = new Uint8Array(out.data);
			width = out.info.width;
			height = out.info.height;
		} catch {
			// No usable poster: the chat shows a plain video tile.
		}
	}
	return { kind: 'video', name: withExtension(name, ext), mime: VIDEO_TYPES[ext], bytes, width, height, thumb, ext, duration: Math.round(seconds) };
}

async function prepareImage(input: Buffer, name: string): Promise<Prepared> {
	if (input.byteLength > MAX_IMAGE_BYTES) throw badRequest('Photos can be up to 15 MB');
	try {
		const meta = await sharp(input, { limitInputPixels: 100_000_000 }).metadata();
		if (meta.format === 'gif' && (meta.pages ?? 1) > 1) {
			// Animated: kept byte for byte so it still moves.
			return {
				kind: 'gif',
				name: withExtension(name, 'gif'),
				mime: 'image/gif',
				bytes: new Uint8Array(input),
				width: meta.width ?? null,
				height: meta.pageHeight ?? meta.height ?? null,
				thumb: null,
				ext: 'gif'
			};
		}
		const png = meta.hasAlpha === true;
		const encode = (side: number) => {
			const img = sharp(input, { limitInputPixels: 100_000_000 }).rotate().resize(side, side, { fit: 'inside', withoutEnlargement: true });
			return (png ? img.png({ compressionLevel: 9 }) : img.jpeg({ quality: 85, mozjpeg: true })).toBuffer({ resolveWithObject: true });
		};
		const [full, thumb] = await Promise.all([encode(PHOTO_SIDE), encode(THUMB_SIDE)]);
		const ext = png ? 'png' : 'jpg';
		return {
			kind: 'image',
			name: withExtension(name, ext),
			mime: png ? 'image/png' : 'image/jpeg',
			bytes: new Uint8Array(full.data),
			width: full.info.width,
			height: full.info.height,
			// A small photo is its own preview.
			thumb: full.data.byteLength > thumb.data.byteLength * 1.5 ? new Uint8Array(thumb.data) : null,
			ext
		};
	} catch (err) {
		if (err instanceof ApiError) throw err;
		throw badRequest("That photo couldn't be read. Try a JPEG, PNG, GIF or WebP.");
	}
}

/**
 * A Wallet pass: kept byte for byte (it is signed), with its card details and logo read from
 * inside. One that can't be read stays a plain file.
 */
async function preparePass(file: Prepared): Promise<Prepared> {
	const read = readWalletPass(file.bytes, file.ext === 'pkpasses');
	if (!read) return file;
	let thumb: Uint8Array | null = null;
	if (read.logo) {
		try {
			const logo = await sharp(read.logo, { limitInputPixels: 25_000_000 })
				.resize(480, 160, { fit: 'inside', withoutEnlargement: true })
				.png({ compressionLevel: 9 })
				.toBuffer();
			thumb = new Uint8Array(logo);
		} catch {
			// A broken logo: the card shows without one.
		}
	}
	return { ...file, kind: 'pass', pass: read.pass, thumb };
}

/**
 * Stores one upload for the group's chat and returns it (not yet in any message). Photos
 * are resized and stripped of metadata (location included); files must be an allowed type.
 */
export async function uploadAttachment(groupId: string, userId: string, file: File, poster: File | null = null): Promise<Attachment> {
	await requireMember(groupId, userId);
	if (file.size === 0) throw badRequest('That file is empty');
	const declared = (file.type || '').toLowerCase();
	let name = cleanName(file.name || 'file');
	// A pass shared without its extension (some apps send "pass" or "ticket").
	if (PASS_MIMES.has(declared) && !(extensionOf(name) in FILE_TYPES)) name = `${name}.${declared.endsWith('pkpasses') ? 'pkpasses' : 'pkpass'}`;
	const ext = extensionOf(name);
	const looksLikeImage = ext in IMAGE_TYPES || Object.values(IMAGE_TYPES).includes(declared);

	let prepared: Prepared;
	if (ext in VIDEO_TYPES) {
		prepared = await prepareVideo(file, name, ext, poster, (await isPro(userId)) ? VIDEO_LIMITS.pro : VIDEO_LIMITS.free);
	} else if (looksLikeImage) {
		if (file.size > MAX_IMAGE_BYTES) throw badRequest('Photos can be up to 15 MB');
		prepared = await prepareImage(Buffer.from(await file.arrayBuffer()), name);
	} else {
		const mime = FILE_TYPES[ext];
		if (!mime) throw badRequest("That type of file can't be sent. Try a PDF, document, spreadsheet, slides, ZIP, audio or video file.");
		if (file.size > MAX_FILE_BYTES) throw badRequest('Files can be up to 25 MB');
		prepared = { kind: 'file', name, mime, bytes: new Uint8Array(await file.arrayBuffer()), width: null, height: null, thumb: null, ext };
		if (PASS_MIMES.has(mime)) prepared = await preparePass(prepared);
	}

	const token = newToken();
	const storageKey = `chat/${groupId}/${token}.${prepared.ext}`;
	const thumbPng = prepared.mime === 'image/png' || prepared.kind === 'pass';
	const thumbKey = prepared.thumb ? `chat/${groupId}/${token}-thumb.${thumbPng ? 'png' : 'jpg'}` : null;
	await putObject(storageKey, prepared.bytes, prepared.mime);
	if (thumbKey && prepared.thumb) await putObject(thumbKey, prepared.thumb, thumbPng ? 'image/png' : 'image/jpeg');

	const id = newId();
	await db.insert(messageAttachments).values({
		id,
		groupId,
		uploaderId: userId,
		kind: prepared.kind,
		name: prepared.name,
		mime: prepared.mime,
		size: prepared.bytes.byteLength,
		width: prepared.width,
		height: prepared.height,
		storageKey,
		thumbKey,
		pass: prepared.pass ?? null,
		duration: prepared.duration ?? null
	});
	return toAttachment((await db.query.messageAttachments.findFirst({ where: eq(messageAttachments.id, id) }))!);
}

/**
 * Checks that every id is the sender's own unsent upload in this group, in the order given.
 * Throws if any isn't.
 */
export async function pendingUploads(
	groupId: string,
	userId: string,
	ids: string[]
): Promise<Pick<AttachmentRow, 'id' | 'kind' | 'name' | 'pass'>[]> {
	const unique = [...new Set(ids)];
	if (unique.length !== ids.length) throw badRequest('An attachment is listed twice');
	if (ids.length > MAX_ATTACHMENTS) throw badRequest(`A message can have up to ${MAX_ATTACHMENTS} attachments`);
	const rows = ids.length
		? await db
				.select({ id: messageAttachments.id, kind: messageAttachments.kind, name: messageAttachments.name, pass: messageAttachments.pass })
				.from(messageAttachments)
				.where(
					and(
						inArray(messageAttachments.id, ids),
						eq(messageAttachments.groupId, groupId),
						eq(messageAttachments.uploaderId, userId),
						isNull(messageAttachments.messageId)
					)
				)
		: [];
	if (rows.length !== ids.length) throw badRequest('An attachment was not found. Try attaching it again.');
	return ids.map((id) => rows.find((r) => r.id === id)!);
}

/** Attaches checked uploads (see pendingUploads) to their message, in order. */
export async function claimUploads(messageId: string, ids: string[]): Promise<void> {
	await Promise.all(ids.map((id, position) => db.update(messageAttachments).set({ messageId, position }).where(eq(messageAttachments.id, id))));
}

// ─── Deleting ─────────────────────────────────────────────────────────────

async function removeRows(rows: Pick<AttachmentRow, 'id' | 'storageKey' | 'thumbKey'>[]): Promise<void> {
	if (!rows.length) return;
	await db.delete(messageAttachments).where(
		inArray(
			messageAttachments.id,
			rows.map((r) => r.id)
		)
	);
	await deleteKeys(rows.flatMap((r) => (r.thumbKey ? [r.storageKey, r.thumbKey] : [r.storageKey])));
}

const keyColumns = { id: messageAttachments.id, storageKey: messageAttachments.storageKey, thumbKey: messageAttachments.thumbKey };

/** Removes a deleted message's attachments, files and all. */
export async function deleteMessageAttachments(messageId: string): Promise<void> {
	await removeRows(await db.select(keyColumns).from(messageAttachments).where(eq(messageAttachments.messageId, messageId)));
}

/** The stored keys of everything sent in a group, read before it is deleted (the rows go with it). */
export async function groupAttachmentKeys(groupId: string): Promise<string[]> {
	const rows = await db.select(keyColumns).from(messageAttachments).where(eq(messageAttachments.groupId, groupId));
	return rows.flatMap((r) => (r.thumbKey ? [r.storageKey, r.thumbKey] : [r.storageKey]));
}

/** Deletes uploads that were never sent. */
export async function deleteAbandonedUploads(now = Date.now()): Promise<number> {
	const rows = await db
		.select(keyColumns)
		.from(messageAttachments)
		.where(and(isNull(messageAttachments.messageId), lt(messageAttachments.createdAt, new Date(now - PENDING_TTL_MS))))
		.limit(500);
	await removeRows(rows);
	return rows.length;
}
