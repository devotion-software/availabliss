import { z } from 'zod';
import { authed, json, query } from '#lib/server/http';
import { myBusy } from '#lib/server/busy';
import { isoDateTime } from '#lib/schemas';

const rangeSchema = z.object({ from: isoDateTime, to: isoDateTime });

export const GET = authed(async ({ url, userId }) => {
	const { from, to } = query(url, rangeSchema);
	return json({ intervals: await myBusy(userId, Date.parse(from), Date.parse(to)) });
});
