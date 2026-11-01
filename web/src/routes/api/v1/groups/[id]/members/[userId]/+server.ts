import { authed, body, json, noContent } from '#lib/server/http';
import { removeMember, setMemberRole } from '#lib/server/groups';
import { onMemberLeft } from '#lib/server/proposals';
import { publishToGroup, publishToUsers } from '#lib/server/realtime';
import { memberRoleSchema } from '#lib/schemas';

export const PATCH = authed<{ id: string; userId: string }>(async ({ event, params, userId }) => {
	const { role } = await body(event, memberRoleSchema);
	const member = await setMemberRole(params.id, userId, params.userId, role);
	await publishToGroup(params.id, 'member.changed', member);
	return json(member);
});

export const DELETE = authed<{ id: string; userId: string }>(async ({ params, userId }) => {
	await removeMember(params.id, userId, params.userId);
	const payload = { userId: params.userId, removed: true };
	await publishToGroup(params.id, 'member.changed', payload);
	publishToUsers([params.userId], { type: 'member.changed', groupId: params.id, payload });
	await onMemberLeft(params.id, params.userId);
	return noContent();
});
