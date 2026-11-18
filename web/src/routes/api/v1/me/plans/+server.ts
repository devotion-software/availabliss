import { authed, json } from '#lib/server/http';
import { myPlans } from '#lib/server/events';

/** Every plan across your groups: going, can't make it, and past. */
export const GET = authed(async ({ userId }) => json(await myPlans(userId)));
