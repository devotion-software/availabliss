/**
 * The chats list (Activity's Chats tab): every group chat you're in, latest message first. Loaded
 * when the page opens and refetched (a moment after) whenever a message arrives, changes or is read.
 */
import type { ChatSummary, MessageReply } from '#lib/types';
import { api } from './api';
import { live } from './live.svelte';
import { replySnippet } from './chat-messages';

export const listChats = () => api.get<{ chats: ChatSummary[] }>('/chats');

/** A wait after a live event before refetching, so a burst of messages is one request. */
const REFRESH_MS = 400;

export class ChatsList {
	items = $state<ChatSummary[]>([]);
	loaded = $state(false);
	failed = $state(false);
	#timer: ReturnType<typeof setTimeout> | undefined;

	async load() {
		try {
			this.items = (await listChats()).chats;
			this.failed = false;
		} catch {
			this.failed = !this.loaded;
		}
		this.loaded = true;
	}

	/** Keeps it current while the page is open; returns the unsubscribe function. */
	listen(): () => void {
		const soon = () => {
			clearTimeout(this.#timer);
			this.#timer = setTimeout(() => this.load(), REFRESH_MS);
		};
		const offs = [live.on('message.created', soon), live.on('message.updated', soon), live.on('notification.created', soon)];
		return () => {
			clearTimeout(this.#timer);
			for (const off of offs) off();
		};
	}
}

/** "You: See you there", "Mo: Poll: Pizza?", "Sam joined" (a group update has no author). */
export function chatPreview(m: ChatSummary['lastMessage'] & {}, meId: string): string {
	const text = replySnippet(m as MessageReply);
	if (!m.authorId) return text;
	const who = m.authorId === meId ? 'You' : (m.authorName?.split(' ')[0] ?? 'Someone');
	return `${who}: ${text}`;
}
