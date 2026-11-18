import { authed, json, query } from '#lib/server/http';
import { myBusy } from '#lib/server/busy';
import { busyQuerySchema } from '#lib/schemas';

export const GET = authed(async ({ url, userId }) => {
	const { from, to } = query(url, busyQuerySchema);
	return json({ intervals: await myBusy(userId, from, to) });
});
