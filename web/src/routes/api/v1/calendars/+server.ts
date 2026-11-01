import { authed, json } from '#lib/server/http';
import { listConnections } from '#lib/server/calendars';

export const GET = authed(async ({ userId }) => json({ connections: await listConnections(userId) }));
