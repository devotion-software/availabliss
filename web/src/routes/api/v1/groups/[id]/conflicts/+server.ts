import { authed, json, query } from '#lib/server/http';
import { memberIds, requireMember } from '#lib/server/groups';
import { busyDuring } from '#lib/server/busy';
import { instantRangeSchema } from '#lib/schemas';

const rangeSchema = instantRangeSchema('start', 'end');

/** Members who are busy during a time, so a proposal can warn before it's sent. */
export const GET = authed<{ id: string }>(async ({ params, url, userId }) => {
	const { from, to } = query(url, rangeSchema);
	await requireMember(params.id, userId);
	return json({ busyMemberIds: await busyDuring(await memberIds(params.id), from, to) });
});
