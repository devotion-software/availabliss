import { authed, json, query } from '#lib/server/http';
import { groupDay } from '#lib/server/availability/service';
import { dayQuerySchema } from '#lib/schemas';

export const GET = authed<{ id: string }>(async ({ params, url, userId }) => {
	const { date } = query(url, dayQuerySchema);
	return json(await groupDay(params.id, userId, date));
});
