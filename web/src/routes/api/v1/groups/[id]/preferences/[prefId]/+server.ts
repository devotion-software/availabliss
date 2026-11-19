import { authed, body, json, noContent } from '#lib/server/http';
import { removePreference, updatePreference } from '#lib/server/preferences';
import { publishToGroup } from '#lib/server/realtime';
import { preferenceSchema } from '#lib/schemas';
import type { Weekday } from '#lib/types';

/** Changes one of your preferred times (same body as adding one). */
export const PUT = authed<{ id: string; prefId: string }>(async ({ event, params, userId }) => {
	const input = await body(event, preferenceSchema);
	const pref = await updatePreference(
		params.id,
		userId,
		params.prefId,
		input.kind === 'weekly' ? { ...input, days: input.days as Weekday[] } : input
	);
	// Clients refetch availability on this, so "2 prefer" updates for everyone.
	await publishToGroup(params.id, 'availability.changed', { userId });
	return json(pref);
});

/** Removes one of your preferred times. */
export const DELETE = authed<{ id: string; prefId: string }>(async ({ params, userId }) => {
	await removePreference(params.id, userId, params.prefId);
	await publishToGroup(params.id, 'availability.changed', { userId });
	return noContent();
});
