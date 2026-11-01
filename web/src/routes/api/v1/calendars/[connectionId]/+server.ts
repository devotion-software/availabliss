import { authed, noContent } from '#lib/server/http';
import { removeConnection } from '#lib/server/calendars';

export const DELETE = authed<{ connectionId: string }>(async ({ params, userId }) => {
	await removeConnection(userId, params.connectionId);
	return noContent();
});
