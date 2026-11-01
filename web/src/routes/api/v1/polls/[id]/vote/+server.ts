import { authed, body, json } from '#lib/server/http';
import { vote } from '#lib/server/chat';
import { voteSchema } from '#lib/schemas';

export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const { optionIds } = await body(event, voteSchema);
	return json(await vote(params.id, userId, optionIds));
});
