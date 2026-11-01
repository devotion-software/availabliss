import { authed, body, json } from '#lib/server/http';
import { createGroup } from '#lib/server/groups';
import { groupSummaries } from '#lib/server/group-summaries';
import { getMe } from '#lib/server/users';
import { createGroupSchema } from '#lib/schemas';

export const GET = authed(async ({ userId }) => json({ groups: await groupSummaries(userId) }));

export const POST = authed(async ({ event, userId }) => {
	const input = await body(event, createGroupSchema);
	const me = await getMe(userId);
	return json(await createGroup(userId, input, me.timezone), { status: 201 });
});
