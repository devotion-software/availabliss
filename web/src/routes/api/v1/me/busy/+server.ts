import { authed, json, query } from '#lib/server/http';
import { myBusy } from '#lib/server/busy';
import { instantRangeSchema } from '#lib/schemas';

const rangeSchema = instantRangeSchema('from', 'to');

export const GET = authed(async ({ url, userId }) => {
	const { from, to } = query(url, rangeSchema);
	return json({ intervals: await myBusy(userId, from, to) });
});
