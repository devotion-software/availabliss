import { authed, json } from '#lib/server/http';
import { getCalendarFeed } from '#lib/server/calendar-feeds';

/** Your secret calendar feed link (made the first time). */
export const GET = authed(async ({ userId }) => json(await getCalendarFeed(userId)));
