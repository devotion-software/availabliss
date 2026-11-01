import { authed, noContent } from '#lib/server/http';
import { unregisterDevice } from '#lib/server/notifications';

export const DELETE = authed<{ token: string }>(async ({ params, userId }) => {
	await unregisterDevice(userId, params.token);
	return noContent();
});
