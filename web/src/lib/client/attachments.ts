/** Pure helpers for chat attachments (photos, GIFs and files): sizes, labels, layout and limits. */
import type { Attachment, Message } from '#lib/types';

/** At most this many attachments in one message. */
export const MAX_ATTACHMENTS = 10;
/** The server's limits: photos (and GIFs) up to 15 MB, other files up to 25 MB. */
export const MAX_IMAGE_BYTES = 15 * 1024 * 1024;
export const MAX_FILE_BYTES = 25 * 1024 * 1024;

/** File extensions the server accepts as files (anything else is a 400). */
export const FILE_EXTENSIONS = [
	'pdf', 'txt', 'md', 'csv', 'rtf', 'ics', 'vcf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'odt', 'ods', 'odp',
	'pages', 'numbers', 'key', 'epub', 'zip', 'mp3', 'm4a', 'wav', 'mp4', 'mov'
] as const;

/** "1.2 MB", "340 KB", "12 bytes". */
export function formatBytes(bytes: number): string {
	if (!Number.isFinite(bytes) || bytes < 0) return '';
	if (bytes < 1024) return bytes === 1 ? '1 byte' : `${bytes} bytes`;
	const units = ['KB', 'MB', 'GB'];
	let value = bytes / 1024;
	let unit = 0;
	while (value >= 1024 && unit < units.length - 1) {
		value /= 1024;
		unit++;
	}
	const rounded = value < 10 ? Math.round(value * 10) / 10 : Math.round(value);
	// 1023.6 KB rounds to 1024 KB: say 1 MB instead.
	if (rounded >= 1024 && unit < units.length - 1) return `1 ${units[unit + 1]}`;
	return `${rounded} ${units[unit]}`;
}

/** The lower-case extension of a file name, without the dot ("" if none). */
export function extensionOf(name: string): string {
	const dot = name.lastIndexOf('.');
	return dot > 0 && dot < name.length - 1 ? name.slice(dot + 1).toLowerCase() : '';
}

/** Shortens a long file name in the middle, keeping its extension: "A very long…plan.pdf". */
export function middleTruncate(name: string, max = 32): string {
	if (name.length <= max) return name;
	const ext = extensionOf(name);
	const tail = Math.max(ext.length + 5, Math.floor((max - 1) / 3));
	const head = max - 1 - tail;
	return `${name.slice(0, head)}…${name.slice(name.length - tail)}`;
}

export type FileIconKind = 'pdf' | 'doc' | 'sheet' | 'slides' | 'zip' | 'audio' | 'video' | 'image' | 'file';

/** Which icon a file card shows, by extension then MIME type. */
export function fileIconKind(name: string, mime = ''): FileIconKind {
	const ext = extensionOf(name);
	if (ext === 'pdf' || mime === 'application/pdf') return 'pdf';
	if (['doc', 'docx', 'odt', 'rtf', 'pages', 'txt', 'md', 'epub'].includes(ext)) return 'doc';
	if (['xls', 'xlsx', 'ods', 'numbers', 'csv'].includes(ext)) return 'sheet';
	if (['ppt', 'pptx', 'odp', 'key'].includes(ext)) return 'slides';
	if (ext === 'zip' || mime === 'application/zip') return 'zip';
	if (['mp3', 'm4a', 'wav'].includes(ext) || mime.startsWith('audio/')) return 'audio';
	if (['mp4', 'mov'].includes(ext) || mime.startsWith('video/')) return 'video';
	if (mime.startsWith('image/')) return 'image';
	return 'file';
}

/** Images and GIFs (shown as pictures) versus files (shown as cards). */
export const isVisual = (a: Pick<Attachment, 'kind'>) => a.kind === 'image' || a.kind === 'gif';

/** What a picture in a bubble loads: a GIF's own (animated) file, a photo's preview. */
export const bubbleSrc = (a: Attachment) => (a.kind === 'gif' ? a.url : (a.thumbnailUrl ?? a.url));

/** A photo or file as a download (photos are served inline otherwise). */
export const downloadUrl = (url: string) => `${url}${url.includes('?') ? '&' : '?'}download=1`;

/** For screen readers: "Photo", "GIF", "File: Plan.pdf, 1.2 MB". */
export function attachmentLabel(a: Pick<Attachment, 'kind' | 'name' | 'size'>): string {
	if (a.kind === 'image') return 'Photo';
	if (a.kind === 'gif') return 'GIF';
	return `File: ${a.name}, ${formatBytes(a.size)}`;
}

/** One line for a message with attachments and no caption: "Photo", "GIF", "3 photos", "Plan.pdf", "2 files", "2 attachments". */
export function attachmentSummary(attachments: Pick<Attachment, 'kind' | 'name'>[]): string {
	const n = attachments.length;
	if (!n) return '';
	if (n === 1) {
		const [a] = attachments;
		return a.kind === 'image' ? 'Photo' : a.kind === 'gif' ? 'GIF' : a.name;
	}
	if (attachments.every((a) => a.kind === 'image')) return `${n} photos`;
	if (attachments.every((a) => a.kind === 'gif')) return `${n} GIFs`;
	if (attachments.every(isVisual)) return `${n} photos`;
	if (attachments.every((a) => a.kind === 'file')) return `${n} files`;
	return `${n} attachments`;
}

/** The text a message shows: with attachments its caption (or nothing), otherwise its body. */
export const messageText = (m: Pick<Message, 'body' | 'caption' | 'attachments'>): string | null =>
	m.attachments?.length ? m.caption : m.body;

/** How big one picture is drawn in a bubble: its aspect ratio, within `maxW` × `maxH` and at least `min` on each side. */
export function fitImage(width: number | null, height: number | null, maxW = 240, maxH = 320, min = 80): { width: number; height: number } {
	if (!width || !height || width <= 0 || height <= 0) return { width: maxW, height: Math.round(maxW * 0.75) };
	const scale = Math.min(maxW / width, maxH / height, 1);
	let w = width * scale;
	let h = height * scale;
	// Very wide or tall pictures still get a usable tile (cropped to fit).
	w = Math.min(maxW, Math.max(min, w));
	h = Math.min(maxH, Math.max(min, h));
	return { width: Math.round(w), height: Math.round(h) };
}

export interface GridTile {
	/** Index into the pictures. */
	index: number;
	/** Spans both columns (the last of an odd three). */
	wide: boolean;
	/** "+N" over the last tile when there are more pictures than tiles. */
	more: number;
}

/**
 * Several pictures in a bubble: a 2-column grid of square tiles, at most four,
 * the last of three spanning both columns, and "+N" over the fourth when there are more.
 */
export function gridTiles(count: number, maxTiles = 4): GridTile[] {
	const shown = Math.min(count, maxTiles);
	return Array.from({ length: shown }, (_, index) => ({
		index,
		wide: shown % 2 === 1 && shown > 1 && index === shown - 1,
		more: index === shown - 1 && count > shown ? count - shown : 0
	}));
}

/** A picked file's kind as the server will see it: GIFs and other images go up as pictures, the rest as files. */
export function pickedKind(file: { name: string; type: string }): 'image' | 'gif' | 'file' {
	if (file.type === 'image/gif' || extensionOf(file.name) === 'gif') return 'gif';
	if (file.type.startsWith('image/') || isHeic(file)) return 'image';
	return 'file';
}

/** HEIC/HEIF photos (from iPhones), which the server may not read: convert them first where the browser can. */
export const isHeic = (file: { name: string; type: string }) =>
	/^image\/hei[cf]/.test(file.type) || ['heic', 'heif'].includes(extensionOf(file.name));

/** Why a picked file can't be sent (checked before uploading), or null if it looks fine. The server has the last word. */
export function pickProblem(file: { name: string; type: string; size: number }): string | null {
	const kind = pickedKind(file);
	if (kind !== 'file') return file.size > MAX_IMAGE_BYTES ? `${file.name} is too big. Photos can be up to 15 MB.` : null;
	if (!(FILE_EXTENSIONS as readonly string[]).includes(extensionOf(file.name)))
		return `${file.name} can’t be sent. Try a PDF, document, spreadsheet, presentation, ZIP, audio or video file.`;
	return file.size > MAX_FILE_BYTES ? `${file.name} is too big. Files can be up to 25 MB.` : null;
}
