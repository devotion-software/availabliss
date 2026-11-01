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

	seed(data: { notifications: AppNotification[]; unread: number }) {
		this.items = data.notifications;
		this.unread = data.unread;
	}

	/** Starts listening for new notifications; returns the unsubscribe function. */
	listen(): () => void {
		return live.on('notification.created', ({ payload }) => {
			if (this.items.some((n) => n.id === payload.id)) return;
			this.items = [payload, ...this.items];
			if (!payload.readAt) this.unread++;
		});
	}

	async markAllRead() {
		if (this.unread === 0) return;
		const now = new Date().toISOString();
		this.items = this.items.map((n) => (n.readAt ? n : { ...n, readAt: now }));
		this.unread = 0;
		await markNotificationsRead().catch(() => {});
	}
}

export const activity = new Activity();
