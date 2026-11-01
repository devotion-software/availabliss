import { authed, json } from '#lib/server/http';
import { syncNow } from '#lib/server/calendars';

export const POST = authed<{ connectionId: string }>(async ({ params, userId }) => json(await syncNow(userId, params.connectionId)));
