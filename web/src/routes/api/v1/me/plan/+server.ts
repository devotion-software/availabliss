import { authed, json } from '#lib/server/http';
import { getPlan } from '#lib/server/billing';

/** Your plan, group allowance and the RevenueCat keys the apps purchase with. */
export const GET = authed(async ({ userId }) => json(await getPlan(userId)));
