import { authed, json, query } from '#lib/server/http';
import { searchMessages } from '#lib/server/chat';
import { messageSearchQuerySchema } from '#lib/schemas';

export const GET = authed<{ id: string }>(async ({ params, url, userId }) => {
	const { q, limit } = query(url, messageSearchQuerySchema);
	return json(await searchMessages(params.id, userId, q, limit));
});
