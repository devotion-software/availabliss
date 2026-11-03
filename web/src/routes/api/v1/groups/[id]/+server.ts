import { authed, body, json, noContent } from '#lib/server/http';
import { deleteGroup, getGroup, updateGroup } from '#lib/server/groups';
import { publishToGroup, publishToUsers } from '#lib/server/realtime';
import { updateGroupSchema } from '#lib/schemas';

export const GET = authed<{ id: string }>(async ({ params, userId }) => json(await getGroup(params.id, userId)));

export const PATCH = authed<{ id: string }>(async ({ event, params, userId }) => {
	const group = await updateGroup(params.id, userId, await body(event, updateGroupSchema));
	await publishToGroup(params.id, 'group.updated', group);
	return json(group);
});

export const DELETE = authed<{ id: string }>(async ({ params, userId }) => {
	const members = await deleteGroup(params.id, userId);
	// The group is gone for everyone: tell each member they're no longer in it.
	for (const id of members) {
		publishToUsers([id], { type: 'member.changed', groupId: params.id, payload: { userId: id, removed: true } });
	}
	return noContent();
});
