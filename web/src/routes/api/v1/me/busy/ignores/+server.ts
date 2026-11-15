import { authed, body, json } from '#lib/server/http';
import { ignoreBusy } from '#lib/server/busy';
import { publishAvailabilityChanged } from '#lib/server/realtime';
import { busyIgnoreSchema } from '#lib/schemas';

/** Marks a calendar event as not busy: one occurrence, or every occurrence of its series. */
export const POST = authed(async ({ event, userId }) => {
	const input = await body(event, busyIgnoreSchema);
	const ignore = await ignoreBusy(userId, input);
	await publishAvailabilityChanged(userId);
	return json(ignore, { status: 201 });
});
