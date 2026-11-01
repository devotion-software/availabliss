import { authed, body, noContent } from '#lib/server/http';
import { markReceipts } from '#lib/server/chat';
import { receiptsSchema } from '#lib/schemas';

export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const { upTo, state } = await body(event, receiptsSchema);
	await markReceipts(params.id, userId, upTo, state);
	return noContent();
});
