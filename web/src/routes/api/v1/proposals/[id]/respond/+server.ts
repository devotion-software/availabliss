import { authed, body, json } from '#lib/server/http';
import { respond } from '#lib/server/proposals';
import { respondSchema } from '#lib/schemas';

export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const { response } = await body(event, respondSchema);
	return json(await respond(params.id, userId, response));
});
