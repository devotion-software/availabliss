import { z } from 'zod';
import { authed, badRequest, json, query } from '#lib/server/http';
import { memberIds, requireMember } from '#lib/server/groups';
import { busyDuring } from '#lib/server/busy';
import { isoDateTime } from '#lib/schemas';

const rangeSchema = z.object({ start: isoDateTime, end: isoDateTime });

/** Members who are busy during a time, so a proposal can warn before it's sent. */
export const GET = authed<{ id: string }>(async ({ params, url, userId }) => {
	const { start, end } = query(url, rangeSchema);
	const from = Date.parse(start);
	const to = Date.parse(end);
	if (to <= from) throw badRequest('The end must be after the start');
	await requireMember(params.id, userId);
	return json({ busyMemberIds: await busyDuring(await memberIds(params.id), from, to) });
});
