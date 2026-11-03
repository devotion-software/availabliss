/**
 * Unread chat counts per group. The server's counts arrive with the groups list
 * (app layout load); live chat events adjust them locally in between, so a new
 * message doesn't refetch every group (and rerun every page load under it).
 */
import type { ReceiptsPayload } from './live.svelte';
import type { Message } from '#lib/types';

class UnreadCounts {
	#base = $state<Record<string, number>>({});
	#extra = $state<Record<string, number>>({});

	/** Takes fresh server counts and drops local adjustments. */
	seed(groups: readonly { id: string; unreadCount?: number }[]) {
		this.#base = Object.fromEntries(groups.map((g) => [g.id, g.unreadCount ?? 0]));
		this.#extra = {};
	}

	/** `group.unreadCount` stands in for groups that weren't in the last seed. */
	count(group: { id: string; unreadCount?: number }): number {
		const base = group.id in this.#base ? this.#base[group.id] : (group.unreadCount ?? 0);
		return Math.max(0, base + (this.#extra[group.id] ?? 0));
	}

	/** A message from someone else (or the system) is unread until seen. */
	onMessage(message: Message, me: string) {
		if (message.authorId === me) return;
		this.#extra[message.groupId] = (this.#extra[message.groupId] ?? 0) + 1;
	}

	/** Our own "seen" receipt means we've read the chat up to now. */
	onReceipts(groupId: string | null, payload: ReceiptsPayload, me: string) {
		if (!groupId || payload.userId !== me || payload.state !== 'seen') return;
		this.#extra[groupId] = -(this.#base[groupId] ?? 0);
	}
}

export const unread = new UnreadCounts();
