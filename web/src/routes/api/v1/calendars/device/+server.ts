import { authed, body, json } from '#lib/server/http';
import { syncDevice } from '#lib/server/calendars';
import { deviceSyncSchema } from '#lib/schemas';

export const PUT = authed(async ({ event, userId }) => json(await syncDevice(userId, await body(event, deviceSyncSchema))));
