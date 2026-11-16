import { toasts } from './toast.svelte';

/** The server's upload limit for photos (avatars, group photos, event covers). */
export const MAX_PHOTO_BYTES = 10 * 1024 * 1024;

/**
 * The photo picked in an `<input type="file">` change event, or null if none (or it's too big,
 * with a toast). Clears the input so picking the same file again still fires `change`.
 */
export function takePhoto(e: Event): File | null {
	const input = e.currentTarget as HTMLInputElement;
	const file = input.files?.[0] ?? null;
	input.value = '';
	if (file && file.size > MAX_PHOTO_BYTES) {
		toasts.error('Photos can be up to 10 MB');
		return null;
	}
	return file;
}
