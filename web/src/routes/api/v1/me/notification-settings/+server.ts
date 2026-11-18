import { authed, body, json } from '#lib/server/http';
import { getNotificationSettings, updateNotificationSettings } from '#lib/server/notification-settings';
import { notificationSettingsSchema } from '#lib/schemas';

export const GET = authed(async ({ userId }) => json(await getNotificationSettings(userId)));

export const PATCH = authed(async ({ event, userId }) =>
	json(await updateNotificationSettings(userId, await body(event, notificationSettingsSchema)))
);
