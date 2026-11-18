/**
 * One open thread of replies: the first message of a chain of replies and every reply under it,
 * fetched from the server (it may reach back past the loaded chat), kept live, with replies
 * sent within it.
 */
import type { Message, Poll } from '#lib/types';
import { live } from './live.svelte';
import { getMessageThread, sendMessage, votePoll } from './chat';
import { applyMessageUpdate, threadReplyTarget, withThreadMessage } from './chat-messages';
import { toasts } from './toast.svelte';

export class ReplyThread {
	messages = $state<Message[]>([]);
	loading = $state(true);
	/** More replies than the server returns (the earliest are kept). */
	truncated = $state(false);
	/** The message you picked to reply to; otherwise replies go to the latest. */
	pickedId = $state<string | null>(null);

	readonly groupId: string;
	readonly meId: string;
	/** The message the thread was opened from. */
	readonly focusId: string;

	constructor(groupId: string, meId: string, focusId: string) {
		this.groupId = groupId;
		this.meId = meId;
		this.focusId = focusId;
	}

	/** Who your reply goes to. */
	replyTarget = $derived.by(() => threadReplyTarget(this.messages, this.pickedId, this.meId));
	/** True when you picked it, rather than it being the latest. */
	picked = $derived(this.pickedId !== null && this.replyTarget?.id === this.pickedId);

	async load() {
		this.loading = true;
		try {
			const thread = await getMessageThread(this.focusId);
			this.messages = thread.messages;
			this.truncated = thread.truncated;
		} catch (err) {
			toasts.error(err);
			throw err;
		} finally {
			this.loading = false;
		}
	}

	/** Replies go to [message]; null goes back to the latest. */
	pick(message: Message | null) {
		this.pickedId = message?.id ?? null;
	}

	/** Sends a reply to the current target. Failures toast and throw (the composer keeps the text). */
	async send(body: string) {
		const text = body.trim();
		const target = this.replyTarget;
		if (!text || !target) return;
		try {
			this.messages = withThreadMessage(this.messages, await sendMessage(this.groupId, text, target.id));
			this.pickedId = null;
		} catch (err) {
			toasts.error(err);
			throw err;
		}
	}

	async vote(pollId: string, optionIds: string[]) {
		try {
			this.#replacePoll(await votePoll(pollId, optionIds));
		} catch (err) {
			toasts.error(err);
		}
	}

	/** Subscribes to live updates for the thread; returns the unsubscribe function. */
	listen(): () => void {
		const offs = [
			live.on('message.created', ({ groupId, payload }) => {
				if ((groupId ?? payload.groupId) === this.groupId) this.messages = withThreadMessage(this.messages, payload);
			}),
			live.on('message.updated', ({ groupId, payload }) => {
				if ((groupId ?? payload.groupId) === this.groupId) this.messages = applyMessageUpdate(this.messages, payload);
			}),
			live.on('poll.updated', ({ groupId, payload }) => {
				if (groupId === null || groupId === this.groupId) this.#replacePoll(payload);
			})
		];
		return () => offs.forEach((off) => off());
	}

	#replacePoll(poll: Poll) {
		this.messages = this.messages.map((m) => (m.poll?.id === poll.id ? { ...m, poll } : m));
	}
}
