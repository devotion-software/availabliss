import { authed, json } from '#lib/server/http';
import { getPlan, syncFromRevenueCat } from '#lib/server/billing';

/** Called by the apps right after a purchase or restore so Pro applies before the webhook lands. */
export const POST = authed(async ({ userId }) => {
	try {
		await syncFromRevenueCat(userId);
	} catch (err) {
		console.warn('[billing] refresh failed', err);
	}
	return json(await getPlan(userId));
});
