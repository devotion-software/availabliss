import { authed, json } from '#lib/server/http';
import { setEventImage } from '#lib/server/events';

/**
 * Sets the event's cover photo (its creator or an admin). Body: the image bytes (any common
 * format), sent as-is with its image/* content type. Returns the updated GroupEvent.
 */
export const PUT = authed<{ id: string }>(async ({ event, params, userId }) =>
	json(await setEventImage(params.id, userId, await event.request.arrayBuffer()))
);

/** Removes the event's cover photo. */
export const DELETE = authed<{ id: string }>(async ({ params, userId }) => json(await setEventImage(params.id, userId, null)));
