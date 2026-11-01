import { listGroups } from '#lib/client/groups';
import { listNotifications } from '#lib/client/notifications';
import type { LayoutLoad } from './$types';

export const load: LayoutLoad = async ({ fetch, data, depends }) => {
	depends('app:groups');
	const [groups, notifications] = await Promise.all([
		listGroups(fetch),
		listNotifications(fetch).catch(() => ({ notifications: [], unread: 0 }))
	]);
	return { ...data, groups, notifications };
};
