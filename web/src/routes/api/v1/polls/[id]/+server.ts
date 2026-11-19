import { authed, body, json } from '#lib/server/http';
import { updatePoll } from '#lib/server/chat';
import { updatePollSchema } from '#lib/schemas';

/** Edits a poll (its author, while it's open): the question, pick-one or pick-any, and a text poll's options. */
export const PATCH = authed<{ id: string }>(async ({ event, params, userId }) =>
	json(await updatePoll(params.id, userId, await body(event, updatePollSchema)))
);
