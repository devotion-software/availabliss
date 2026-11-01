import { authed, body, json, noContent } from '#lib/server/http';
import { deleteGroup, getGroup, updateGroup } from '#lib/server/groups';
import { publishToGroup } from '#lib/server/realtime';
import { updateGroupSchema } from '#lib/schemas';

export const GET = authed<{ id: string }>(async ({ params, userId }) => json(await getGroup(params.id, userId)));

export const PATCH = authed<{ id: string }>(async ({ event, params, userId }) => {
	const group = await updateGroup(params.id, userId, await body(event, updateGroupSchema));
	await publishToGroup(params.id, 'group.updated', group);
	return json(group);
});

export const DELETE = authed<{ id: string }>(async ({ params, userId }) => {
	await deleteGroup(params.id, userId);
	return noContent();
});
