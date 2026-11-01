import { authed, json } from '#lib/server/http';
import { upcomingFor } from '#lib/server/events';

export const GET = authed(async ({ userId }) => json({ events: await upcomingFor(userId) }));
