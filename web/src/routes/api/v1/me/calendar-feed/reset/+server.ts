import { authed, json } from '#lib/server/http';
import { resetCalendarFeed } from '#lib/server/calendar-feeds';

/** A new feed link; the old one stops working. */
export const POST = authed(async ({ userId }) => json(await resetCalendarFeed(userId)));
