import { authed, body, json, noContent } from '#lib/server/http';
import { removeMember, setMemberDisplayName, setMemberRole } from '#lib/server/groups';
import { onMemberLeft } from '#lib/server/proposals';
import { publishToGroup, publishToUsers } from '#lib/server/realtime';
import { memberUpdateSchema } from '#lib/schemas';

/** Change a member: their role (admins only), and/or their name in the group (yourself, or an admin for anyone). */
export const PATCH = authed<{ id: string; userId: string }>(async ({ event, params, userId }) => {
	const { role, displayName } = await body(event, memberUpdateSchema);
	let member = role !== undefined ? await setMemberRole(params.id, userId, params.userId, role) : null;
	if (displayName !== undefined) member = await setMemberDisplayName(params.id, userId, displayName, params.userId);
	await publishToGroup(params.id, 'member.changed', member!);
	return json(member!);
});

export const DELETE = authed<{ id: string; userId: string }>(async ({ params, userId }) => {
	await removeMember(params.id, userId, params.userId);
	const payload = { userId: params.userId, removed: true };
	await publishToGroup(params.id, 'member.changed', payload);
	publishToUsers([params.userId], { type: 'member.changed', groupId: params.id, payload });
	await onMemberLeft(params.id, params.userId);
	return noContent();
});
