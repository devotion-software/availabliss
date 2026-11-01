import { authed, json, query } from '#lib/server/http';
import { groupAvailability } from '#lib/server/availability/service';
import { rangeQuerySchema } from '#lib/schemas';

export const GET = authed<{ id: string }>(async ({ params, url, userId }) => {
	const { from, to } = query(url, rangeQuerySchema);
	return json(await groupAvailability(params.id, userId, from, to));
});
