import { authed, body, json } from '#lib/server/http';
import { muteGroup, unmuteGroup } from '#lib/server/notification-settings';
import { muteGroupSchema } from '#lib/schemas';

/** Mutes the group's pushes for you until `until` (null = until you unmute). */
export const PUT = authed<{ id: string }>(async ({ event, params, userId }) => {
	const { until } = await body(event, muteGroupSchema);
	return json(await muteGroup(userId, params.id, until));
});

export const DELETE = authed<{ id: string }>(async ({ params, userId }) => json(await unmuteGroup(userId, params.id)));
