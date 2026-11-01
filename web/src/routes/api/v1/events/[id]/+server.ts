import { authed, body, json, noContent } from '#lib/server/http';
import { deleteEvent, getEvent, updateEvent } from '#lib/server/events';
import { updateEventSchema } from '#lib/schemas';

export const GET = authed<{ id: string }>(async ({ params, userId }) => json(await getEvent(params.id, userId)));

export const PATCH = authed<{ id: string }>(async ({ event, params, userId }) =>
	json(await updateEvent(params.id, userId, await body(event, updateEventSchema)))
);

export const DELETE = authed<{ id: string }>(async ({ params, userId }) => {
	await deleteEvent(params.id, userId);
	return noContent();
});
