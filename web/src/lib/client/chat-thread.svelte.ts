/**
 * Live state for one group's chat: the loaded messages (newest last), sending
 * with optimistic inserts, polls, live updates and read receipts.
 * Used by the full chat screen and the desktop chat panel alike.
 */
import type { Message, Poll } from '#lib/types';
import { live, receiptUpdates } from './live.svelte';
import { closePoll, listMessages, sendMessage, sendPoll, sendReceipt, votePoll, type MessagePage, type PollInput } from './chat';
import { toasts } from './toast.svelte';

const PAGE = 50;
let tempSeq = 0;

/** Strict "is `a` newer than `b`" on messages (createdAt, then id). */
const newer = (a: Message, b: Message | null) => !b || a.createdAt > b.createdAt || (a.createdAt === b.createdAt && a.id > b.id);
const isTemp = (m: Message) => m.id.startsWith('temp-');

export class ChatThread {
	messages = $state<Message[]>([]);
	hasMore = $state(false);
	loading = $state(false);
	loadingOlder = $state(false);
	/** Messages from others that arrived since loading and haven't been seen yet. */
	unseen = $state(0);

	readonly groupId: string;
	readonly meId: string;
	#recipients: number;
	#visible = false;
	#delivered: Message | null = null;
	#seen: Message | null = null;
	#deliveredTimer: ReturnType<typeof setTimeout> | undefined;
	#seenTimer: ReturnType<typeof setTimeout> | undefined;

	constructor(groupId: string, meId: string, memberCount: number, initial?: MessagePage) {
		this.groupId = groupId;
		this.meId = meId;
		this.#recipients = Math.max(0, memberCount - 1);
		if (initial) this.#seed(initial);
	}

	#seed(page: MessagePage) {
		this.messages = page.messages;
		this.hasMore = page.hasMore;
		this.unseen = 0;
		this.#queueDelivered();
		this.#queueSeen();
	}

	async load() {
		this.loading = true;
		try {
			this.#seed(await listMessages(this.groupId, { limit: PAGE }));
		} catch (err) {
			toasts.error(err);
		} finally {
			this.loading = false;
		}
	}

	async loadOlder() {
		const oldest = this.messages.find((m) => !isTemp(m));
		if (!oldest || this.loadingOlder) return;
		this.loadingOlder = true;
		try {
			const page = await listMessages(this.groupId, { before: oldest.id, limit: PAGE });
			const known = new Set(this.messages.map((m) => m.id));
			this.messages = [...page.messages.filter((m) => !known.has(m.id)), ...this.messages];
			this.hasMore = page.hasMore;
		} catch (err) {
			toasts.error(err);
		} finally {
			this.loadingOlder = false;
		}
	}

	async send(body: string) {
		const text = body.trim();
		if (!text) return;
		const temp: Message = {
			id: `temp-${++tempSeq}`,
			groupId: this.groupId,
			authorId: this.meId,
			kind: 'text',
			body: text,
			poll: null,
			proposalId: null,
			eventId: null,
			createdAt: new Date().toISOString(),
			receipt: { delivered: 0, seen: 0, recipients: this.#recipients, seenBy: [] }
		};
		this.messages = [...this.messages, temp];
		try {
			this.#settle(temp.id, await sendMessage(this.groupId, text));
		} catch (err) {
			this.messages = this.messages.filter((m) => m.id !== temp.id);
			toasts.error(err);
			throw err;
		}
	}

	async sendPoll(poll: PollInput) {
		const message = await sendPoll(this.groupId, poll);
		this.#insert(message);
	}

	async vote(pollId: string, optionIds: string[]) {
		const before = this.#findPoll(pollId);
		if (!before) return;
		this.#replacePoll({
			...before,
			options: before.options.map((o) => ({
				...o,
				voterIds: [...o.voterIds.filter((id) => id !== this.meId), ...(optionIds.includes(o.id) ? [this.meId] : [])]
			}))
		});
		try {
			this.#replacePoll(await votePoll(pollId, optionIds));
		} catch (err) {
			this.#replacePoll(before);
			toasts.error(err);
		}
	}

	async closePoll(pollId: string) {
		try {
			this.#replacePoll(await closePoll(pollId));
		} catch (err) {
			toasts.error(err);
		}
	}

	/** The list reports whether its newest messages are on screen. */
	setVisible(visible: boolean) {
		this.#visible = visible;
		if (visible) this.#queueSeen();
	}

	/** Subscribes to live updates for this group; returns the unsubscribe function. */
	listen(): () => void {
		const offs = [
			live.on('message.created', ({ groupId, payload }) => {
				if ((groupId ?? payload.groupId) !== this.groupId) return;
				this.#insert(payload);
				if (payload.authorId !== this.meId) {
					this.unseen++;
					this.#queueDelivered();
					this.#queueSeen();
				}
			}),
			live.on('poll.updated', ({ groupId, payload }) => {
				if (groupId === null || groupId === this.groupId) this.#replacePoll(payload);
			}),
			live.on('message.receipts', ({ groupId, payload }) => {
				if (groupId !== null && groupId !== this.groupId) return;
				const updates = new Map(receiptUpdates(payload).map((u) => [u.id, u.receipt]));
				if (!updates.size) return;
				this.messages = this.messages.map((m) => (updates.has(m.id) ? { ...m, receipt: updates.get(m.id)! } : m));
			})
		];
		const onVisibility = () => this.#queueSeen();
		document.addEventListener('visibilitychange', onVisibility);
		return () => {
			offs.forEach((off) => off());
			document.removeEventListener('visibilitychange', onVisibility);
			clearTimeout(this.#deliveredTimer);
			clearTimeout(this.#seenTimer);
		};
	}

	/** Adds a message, replacing a matching optimistic one or an existing copy. */
	#insert(message: Message) {
		if (this.messages.some((m) => m.id === message.id)) return;
		const temp =
			message.authorId === this.meId && message.kind === 'text'
				? this.messages.find((m) => isTemp(m) && m.body === message.body)
				: undefined;
		if (temp) {
			this.messages = this.messages.map((m) => (m.id === temp.id ? message : m));
			return;
		}
		const next = [...this.messages, message];
		next.sort((a, b) => (a.createdAt < b.createdAt ? -1 : a.createdAt > b.createdAt ? 1 : 0));
		this.messages = next;
	}

	/** Swaps an optimistic message for the server's copy (or drops it if live got there first). */
	#settle(tempId: string, message: Message) {
		if (this.messages.some((m) => m.id === message.id)) this.messages = this.messages.filter((m) => m.id !== tempId);
		else this.messages = this.messages.map((m) => (m.id === tempId ? message : m));
	}

	#findPoll(pollId: string): Poll | undefined {
		return this.messages.find((m) => m.poll?.id === pollId)?.poll ?? undefined;
	}

	#replacePoll(poll: Poll) {
		this.messages = this.messages.map((m) => (m.poll?.id === poll.id ? { ...m, poll } : m));
	}

	/** Newest message someone else wrote (system messages included). */
	#newestFromOthers(): Message | null {
		for (let i = this.messages.length - 1; i >= 0; i--) {
			const m = this.messages[i];
			if (!isTemp(m) && m.authorId !== this.meId) return m;
		}
		return null;
	}

	#queueDelivered() {
		clearTimeout(this.#deliveredTimer);
		this.#deliveredTimer = setTimeout(() => {
			const target = this.#newestFromOthers();
			if (!target || !newer(target, this.#delivered)) return;
			this.#delivered = target;
			sendReceipt(this.groupId, target.id, 'delivered').catch(() => {});
		}, 300);
	}

	#queueSeen() {
		clearTimeout(this.#seenTimer);
		this.#seenTimer = setTimeout(() => {
			if (!this.#visible || typeof document === 'undefined' || document.visibilityState !== 'visible') return;
			this.unseen = 0;
			const target = this.#newestFromOthers();
			if (!target || !newer(target, this.#seen)) return;
			this.#seen = target;
			if (newer(target, this.#delivered)) this.#delivered = target;
			sendReceipt(this.groupId, target.id, 'seen').catch(() => {});
		}, 500);
	}
}
