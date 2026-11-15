import { authed, body, json } from '#lib/server/http';
import { addPreference, listPreferences } from '#lib/server/preferences';
import { publishToGroup } from '#lib/server/realtime';
import { preferenceSchema } from '#lib/schemas';
import type { Weekday } from '#lib/types';

/** Every member's preferred times for the group. */
export const GET = authed<{ id: string }>(async ({ params, userId }) => json({ preferences: await listPreferences(params.id, userId) }));

/** Adds one of your preferred times. */
export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const input = await body(event, preferenceSchema);
	const pref = await addPreference(params.id, userId, input.kind === 'weekly' ? { ...input, days: input.days as Weekday[] } : input);
	// Clients refetch availability on this, so "2 prefer" updates for everyone.
	await publishToGroup(params.id, 'availability.changed', { userId });
	return json(pref, { status: 201 });
});
