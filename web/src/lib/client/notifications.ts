import { api, type Fetch } from './api';
import type { AppNotification } from '#lib/types';

export const listNotifications = (fetch?: Fetch) =>
	api.get<{ notifications: AppNotification[]; unread: number }>('/notifications', { fetch });
/** Marks the given notifications read, or all of them when `ids` is omitted. */
export const markNotificationsRead = (ids?: string[]) => api.post('/notifications/read', ids ? { ids } : {});

/** Where tapping a notification should take you. */
export function notificationHref(n: AppNotification): string {
	if (n.kind === 'invite.received' && n.data.token) return `/invite/${n.data.token}`;
	if (n.data.proposalId) return `/app/proposals/${n.data.proposalId}`;
	if (n.groupId && (n.kind === 'message' || n.data.messageId)) return `/app/groups/${n.groupId}/chat?from=activity`;
	if (n.groupId) return `/app/groups/${n.groupId}`;
	return '/app';
}
