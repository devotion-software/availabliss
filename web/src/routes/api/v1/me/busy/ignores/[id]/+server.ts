import { authed, noContent } from '#lib/server/http';
import { unignoreBusy } from '#lib/server/busy';
import { publishAvailabilityChanged } from '#lib/server/realtime';

/** The event counts as busy again. */
export const DELETE = authed<{ id: string }>(async ({ params, userId }) => {
	await unignoreBusy(userId, params.id);
	await publishAvailabilityChanged(userId);
	return noContent();
});
