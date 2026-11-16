import { authed, body, json, noContent } from '#lib/server/http';
import { deleteMessage, editMessage } from '#lib/server/chat';
import { editMessageSchema } from '#lib/schemas';

export const PATCH = authed<{ id: string }>(async ({ event, params, userId }) => {
	const input = await body(event, editMessageSchema);
	return json(await editMessage(params.id, userId, input.body));
});

export const DELETE = authed<{ id: string }>(async ({ params, userId }) => {
	await deleteMessage(params.id, userId);
	return noContent();
});
