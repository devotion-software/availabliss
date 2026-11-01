import { authed, body, noContent } from '#lib/server/http';
import { markRead } from '#lib/server/notifications';
import { readNotificationsSchema } from '#lib/schemas';

export const POST = authed(async ({ event, userId }) => {
	const { ids } = await body(event, readNotificationsSchema);
	await markRead(userId, ids);
	return noContent();
});
