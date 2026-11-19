import { authed, body, json } from '#lib/server/http';
import { removeReaction, setReaction } from '#lib/server/chat';
import { reactionSchema } from '#lib/schemas';

/** React to a message (replacing your earlier reaction). */
export const PUT = authed<{ id: string }>(async ({ event, params, userId }) => {
	const input = await body(event, reactionSchema);
	return json(await setReaction(params.id, userId, input.emoji));
});

/** Take back your reaction. */
export const DELETE = authed<{ id: string }>(async ({ params, userId }) => json(await removeReaction(params.id, userId)));
