/** Notification settings (docs/API.md "Notification settings"): categories off, quiet hours, muted groups. */
import { api, type Fetch } from './api';
import type { NotificationCategory, NotificationSettings } from '#lib/types';

export type NotificationSettingsPatch = Partial<Pick<NotificationSettings, 'off' | 'quietHours'>>;

export const getNotificationSettings = (fetch?: Fetch) => api.get<NotificationSettings>('/me/notification-settings', { fetch });
export const updateNotificationSettings = (patch: NotificationSettingsPatch) =>
	api.patch<NotificationSettings>('/me/notification-settings', patch);

/** Mutes a group's pushes until an ISO instant, or until you unmute it (`null`). */
export const muteGroup = (groupId: string, until: string | null) =>
	api.put<NotificationSettings>(`/groups/${encodeURIComponent(groupId)}/mute`, { until });
export const unmuteGroup = (groupId: string) => api.del<NotificationSettings>(`/groups/${encodeURIComponent(groupId)}/mute`);

export const CATEGORY_LABELS: Record<NotificationCategory, string> = {
	chat: 'Chat messages',
	proposals: 'New proposals and changes',
	plans: 'Confirmed plans',
	reminders: 'Reminders',
	nudges: 'Nudges to add busy times'
};

/** The group's active mute, if any (one whose `until` has passed no longer counts). */
export function groupMute(settings: NotificationSettings | null, groupId: string, now = Date.now()) {
	const mute = settings?.mutes.find((m) => m.groupId === groupId);
	if (!mute) return null;
	if (mute.until && Date.parse(mute.until) <= now) return null;
	return mute;
}
