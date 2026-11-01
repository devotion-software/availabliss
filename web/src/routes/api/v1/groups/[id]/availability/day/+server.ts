import { z } from 'zod';
import { authed, json, query } from '#lib/server/http';
import { groupDay } from '#lib/server/availability/service';
import { dateString } from '#lib/schemas';

export const GET = authed<{ id: string }>(async ({ params, url, userId }) => {
	const { date } = query(url, z.object({ date: dateString }));
	return json(await groupDay(params.id, userId, date));
});
