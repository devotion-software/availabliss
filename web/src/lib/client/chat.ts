import { api, apiUrl, ApiError, type Fetch } from './api';
import type { ApiErrorBody, Attachment, Message, MessageInfo, MessageLocation, MessageThread, Poll } from '#lib/types';

export interface MessagePage {
	messages: Message[];
	hasMore: boolean;
}

export interface PollInput {
	question: string;
	options: string[];
	multi?: boolean;
}

export const listMessages = (groupId: string, opts: { before?: string; limit?: number } = {}, fetch?: Fetch) =>
	api.get<MessagePage>(`/groups/${groupId}/messages`, { fetch, query: { before: opts.before, limit: opts.limit ?? 50 } });
/** A text message, or (with `attachmentIds`) uploaded attachments with `body` as their optional caption. */
export const sendMessage = (groupId: string, body: string, replyToId?: string | null, attachmentIds: string[] = []) =>
	api.post<Message>(`/groups/${groupId}/messages`, {
		...(attachmentIds.length ? { attachmentIds, ...(body && { body }) } : { body }),
		...(replyToId && { replyToId })
	});
/** Shares a place (your position, or one you found). */
export const sendLocation = (groupId: string, location: MessageLocation, replyToId?: string | null) =>
	api.post<Message>(`/groups/${groupId}/messages`, { location, ...(replyToId && { replyToId }) });
/** A date poll: 2–5 times from the calendar for the group to vote on (pick any, by default). */
export interface DatePollInput {
	question: string;
	slots: { start: string; end: string; allDay?: boolean }[];
	multi?: boolean;
}

export const sendPoll = (groupId: string, poll: PollInput | DatePollInput) => api.post<Message>(`/groups/${groupId}/messages`, { poll });
export const sendReceipt = (groupId: string, upTo: string, state: 'delivered' | 'seen') =>
	api.post(`/groups/${groupId}/messages/receipts`, { upTo, state });
export const getMessageInfo = (messageId: string) => api.get<MessageInfo>(`/messages/${messageId}/info`);
/** The thread a message is part of: its first message and every reply under it, oldest first. */
export const getMessageThread = (messageId: string) => api.get<MessageThread>(`/messages/${messageId}/thread`);
export const votePoll = (pollId: string, optionIds: string[]) => api.post<Poll>(`/polls/${pollId}/vote`, { optionIds });
export const closePoll = (pollId: string) => api.post<Poll>(`/polls/${pollId}/close`);
/** Edits a poll (its author): the question, pick-one / pick-any, and a text poll's options ({ id } keeps one and its votes). */
export interface PollPatch {
	question?: string;
	multi?: boolean;
	options?: { id?: string; label: string }[];
}
export const updatePoll = (pollId: string, patch: PollPatch) => api.patch<Poll>(`/polls/${pollId}`, patch);
export const editMessage = (messageId: string, body: string) => api.patch<Message>(`/messages/${messageId}`, { body });
export const deleteMessage = (messageId: string) => api.del(`/messages/${messageId}`);

/**
 * Uploads one photo, GIF or file for the group's chat (not yet sent: pass its id to `sendMessage`).
 * Uses XMLHttpRequest for upload progress (0–1); the session cookie authenticates. Rejects with an
 * `ApiError` carrying the server's message (e.g. a file type it doesn't take), or a TypeError when offline.
 */
export function uploadAttachment(
	groupId: string,
	file: File,
	opts: { onprogress?: (fraction: number) => void; signal?: AbortSignal } = {}
): Promise<Attachment> {
	return new Promise((resolve, reject) => {
		const xhr = new XMLHttpRequest();
		xhr.open('POST', apiUrl(`/groups/${groupId}/attachments`));
		xhr.withCredentials = true;
		xhr.setRequestHeader('accept', 'application/json');
		xhr.responseType = 'text';
		xhr.upload.onprogress = (e) => {
			if (e.lengthComputable) opts.onprogress?.(e.loaded / e.total);
		};
		xhr.onload = () => {
			let body: unknown = null;
			try {
				body = xhr.responseText ? JSON.parse(xhr.responseText) : null;
			} catch {
				// Not JSON (a proxy's error page): fall back to the generic message.
			}
			if (xhr.status >= 200 && xhr.status < 300) resolve(body as Attachment);
			else reject(new ApiError(xhr.status, body as Partial<ApiErrorBody> | null));
		};
		xhr.onerror = () => reject(new TypeError('Network error'));
		xhr.onabort = () => reject(new DOMException('Upload cancelled', 'AbortError'));
		opts.signal?.addEventListener('abort', () => xhr.abort(), { once: true });
		const form = new FormData();
		form.append('file', file, file.name);
		xhr.send(form);
	});
}
