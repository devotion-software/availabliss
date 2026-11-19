import { authed, json } from '#lib/server/http';
import { setGroupImage } from '#lib/server/groups';
import { publishGroupUpdated } from '#lib/server/realtime';

/**
 * Sets the group's photo (admins). Body: the image bytes (any common format), sent as-is
 * with its image/* content type. Returns the updated Group.
 */
export const PUT = authed<{ id: string }>(async ({ event, params, userId }) => {
	const group = await setGroupImage(params.id, userId, await event.request.arrayBuffer());
	await publishGroupUpdated(group);
	return json(group);
});

/** Removes the group's photo (back to the coloured monogram tile). */
export const DELETE = authed<{ id: string }>(async ({ params, userId }) => {
	const group = await setGroupImage(params.id, userId, null);
	await publishGroupUpdated(group);
	return json(group);
});
