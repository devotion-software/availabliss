import { authed, json } from '#lib/server/http';
import { listNotifications } from '#lib/server/notifications';

export const GET = authed(async ({ userId }) => json(await listNotifications(userId)));
