/**
 * The signed-in user's notifications ("Activity"): seeded from the app layout's
 * load, kept live from /api/v1/live, shared by the nav bell and the Activity page.
 */
import type { AppNotification } from '#lib/types';
import { live } from './live.svelte';
import { markNotificationsRead } from './notifications';

class Activity {
	items = $state<AppNotification[]>([]);
	unread = $state(0);
	/**
	 * False in SSR and until the app layout seeds it on the client. This is a
	 * module-level singleton, so it must never be seeded on the server (it would be
	 * shared between requests): until then, read the layout's data instead.
	 */
	seeded = $state(false);

	seed(data: { notifications: AppNotification[]; unread: number }) {
		this.items = data.notifications;
		this.unread = data.unread;
		this.seeded = true;
	}

	/** Starts listening for new notifications; returns the unsubscribe function. */
	listen(): () => void {
		return live.on('notification.created', ({ payload }) => this.upsert(payload));
	}

	/**
	 * A new item, or an update to one we have (a group's chat item gets a new
	 * message, or is read): replaced in place, or moved to the top when newer.
	 */
	upsert(payload: AppNotification) {
		const prev = this.items.find((n) => n.id === payload.id);
		if (prev && prev.createdAt === payload.createdAt && prev.readAt === payload.readAt && prev.body === payload.body) return;
		const rest = this.items.filter((n) => n.id !== payload.id);
		this.items = prev && payload.createdAt <= prev.createdAt ? this.items.map((n) => (n.id === payload.id ? payload : n)) : [payload, ...rest];
		this.unread += (payload.readAt ? 0 : 1) - (prev && !prev.readAt ? 1 : 0);
	}

	/** Everything but chats, which stay unread until the chat is read. */
	async markAllRead() {
		if (!this.items.some((n) => !n.readAt && n.kind !== 'message')) return;
		const now = new Date().toISOString();
		this.items = this.items.map((n) => (n.readAt || n.kind === 'message' ? n : { ...n, readAt: now }));
		this.unread = this.items.filter((n) => !n.readAt).length;
		await markNotificationsRead().catch(() => {});
	}
}

export const activity = new Activity();
