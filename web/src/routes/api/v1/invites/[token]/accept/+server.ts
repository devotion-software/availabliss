import { authed, json } from '#lib/server/http';
import { acceptInvite, getGroup } from '#lib/server/groups';
import { onMemberJoined } from '#lib/server/proposals';
import { publishToGroup } from '#lib/server/realtime';
import { notify } from '#lib/server/notifications';

export const POST = authed<{ token: string }>(async ({ params, userId }) => {
	const { groupId, joined: isNew } = await acceptInvite(params.token, userId);
	const group = await getGroup(groupId, userId);
	// Already a member (or a second tap): nothing changed, so nobody is told.
	if (!isNew) return json(group);
	await onMemberJoined(groupId, userId);
	const joined = group.members.find((m) => m.userId === userId);
	await publishToGroup(groupId, 'member.changed', joined);
	await notify(
		group.members.filter((m) => m.userId !== userId && m.role !== 'member').map((m) => m.userId),
		{ kind: 'invite.accepted', groupId, title: group.name, body: `${joined?.name ?? 'Someone'} joined the group` }
	);
	return json(group);
});
