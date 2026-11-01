import { authed, body, noContent } from '#lib/server/http';
import { registerDevice } from '#lib/server/notifications';
import { deviceSchema } from '#lib/schemas';

export const POST = authed(async ({ event, userId }) => {
	await registerDevice(userId, await body(event, deviceSchema));
	return noContent();
});
