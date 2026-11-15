import { authed, noContent } from '#lib/server/http';
import { removePreference } from '#lib/server/preferences';
import { publishToGroup } from '#lib/server/realtime';

/** Removes one of your preferred times. */
export const DELETE = authed<{ id: string; prefId: string }>(async ({ params, userId }) => {
	await removePreference(params.id, userId, params.prefId);
	await publishToGroup(params.id, 'availability.changed', { userId });
	return noContent();
});
