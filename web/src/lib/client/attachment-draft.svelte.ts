/**
 * The attachments picked for the next message, above the composer: each uploads as soon as it's
 * picked (with progress), can be removed, and on failure retried. Send is ready once nothing is
 * still uploading and at least one has uploaded.
 */
import type { Attachment } from '#lib/types';
import { errorMessage } from './api';
import { uploadAttachment } from './chat';
import { MAX_ATTACHMENTS, MAX_IMAGE_BYTES, isHeic, pickProblem, pickedKind } from './attachments';
import { toasts } from './toast.svelte';

export interface DraftAttachment {
	key: number;
	file: File;
	kind: 'image' | 'gif' | 'file';
	/** An object URL for a picture's thumbnail in the tray (null for files). */
	preview: string | null;
	status: 'uploading' | 'done' | 'error';
	/** 0–1 while uploading. */
	progress: number;
	error: string | null;
	attachment: Attachment | null;
	abort: AbortController | null;
}

let seq = 0;

export class AttachmentDraft {
	items = $state<DraftAttachment[]>([]);

	readonly groupId: string;

	constructor(groupId: string) {
		this.groupId = groupId;
	}

	busy = $derived(this.items.some((i) => i.status === 'uploading'));
	/** Ids to send, in the order they were picked. */
	ids = $derived(this.items.filter((i) => i.status === 'done' && i.attachment).map((i) => i.attachment!.id));
	/** Ready to send: something uploaded and nothing still uploading or failed. */
	ready = $derived(this.ids.length > 0 && this.items.every((i) => i.status === 'done'));

	/** Adds picked, pasted or dropped files (up to 10 in all), starting their uploads. */
	add(files: Iterable<File>) {
		const list = [...files];
		const room = MAX_ATTACHMENTS - this.items.length;
		if (list.length > room) toasts.error(`You can send up to ${MAX_ATTACHMENTS} at a time`);
		for (const file of list.slice(0, Math.max(0, room))) {
			const problem = pickProblem(file);
			if (problem) {
				toasts.error(problem);
				continue;
			}
			const kind = pickedKind(file);
			const item: DraftAttachment = {
				key: ++seq,
				file,
				kind,
				preview: kind !== 'file' && !isHeic(file) ? URL.createObjectURL(file) : null,
				status: 'uploading',
				progress: 0,
				error: null,
				attachment: null,
				abort: null
			};
			this.items = [...this.items, item];
			this.#upload(item.key);
		}
	}

	remove(key: number) {
		const item = this.items.find((i) => i.key === key);
		if (!item) return;
		item.abort?.abort();
		if (item.preview) URL.revokeObjectURL(item.preview);
		this.items = this.items.filter((i) => i.key !== key);
	}

	retry(key: number) {
		const item = this.items.find((i) => i.key === key);
		if (item?.status === 'error') this.#upload(key);
	}

	/** Empties the tray (after sending, or leaving the chat): cancels uploads and frees previews. */
	clear() {
		for (const item of this.items) {
			item.abort?.abort();
			if (item.preview) URL.revokeObjectURL(item.preview);
		}
		this.items = [];
	}

	#patch(key: number, patch: Partial<DraftAttachment>) {
		this.items = this.items.map((i) => (i.key === key ? { ...i, ...patch } : i));
	}

	async #upload(key: number) {
		const item = this.items.find((i) => i.key === key);
		if (!item) return;
		const abort = new AbortController();
		this.#patch(key, { status: 'uploading', progress: 0, error: null, abort });
		try {
			const file = isHeic(item.file) || (item.kind === 'image' && item.file.size > MAX_IMAGE_BYTES) ? await toJpeg(item.file) : item.file;
			// The converted photo can show in the tray now.
			if (file !== item.file && !item.preview) this.#patch(key, { preview: URL.createObjectURL(file) });
			const attachment = await uploadAttachment(this.groupId, file, {
				signal: abort.signal,
				onprogress: (progress) => this.#patch(key, { progress })
			});
			if (abort.signal.aborted) return;
			this.#patch(key, { status: 'done', progress: 1, attachment, abort: null });
		} catch (err) {
			if (abort.signal.aborted) return;
			this.#patch(key, { status: 'error', error: errorMessage(err), abort: null });
			toasts.error(err);
		}
	}
}

/** The longest side the server keeps a photo at (it resizes anything bigger). */
const PHOTO_SIDE = 2560;

/**
 * HEIC/HEIF photos, and photos too big to upload, become a JPEG no bigger than the server keeps
 * (2560 px), where the browser can decode them (HEIC: Safari). Otherwise the original goes up and
 * the server has the last word.
 */
async function toJpeg(file: File): Promise<File> {
	try {
		const bitmap = await createImageBitmap(file);
		const scale = Math.min(1, PHOTO_SIDE / Math.max(bitmap.width, bitmap.height));
		const canvas = document.createElement('canvas');
		canvas.width = Math.round(bitmap.width * scale);
		canvas.height = Math.round(bitmap.height * scale);
		canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
		bitmap.close();
		const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.9));
		if (!blob) return file;
		return new File([blob], file.name.replace(/\.(heic|heif|jpe?g|png|webp|avif|tiff?)$/i, '') + '.jpg', { type: 'image/jpeg' });
	} catch {
		return file;
	}
}
