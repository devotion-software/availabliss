import { authed, body, json } from '#lib/server/http';
import { override } from '#lib/server/proposals';
import { overrideSchema } from '#lib/schemas';

export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const input = await body(event, overrideSchema);
	return json(await override(params.id, userId, input.userId, input.response));
});
