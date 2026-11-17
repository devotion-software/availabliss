import { authed, body, noContent } from '#lib/server/http';
import { leaveGroup } from '#lib/server/groups';
import { onMemberLeft } from '#lib/server/proposals';
import { publishToGroup, publishToUsers } from '#lib/server/realtime';
import { leaveGroupSchema } from '#lib/schemas';

/** Leave the group; the owner names the admin who takes over (docs/API.md "Leaving a group"). */
export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const { newOwnerId } = await body(event, leaveGroupSchema);
	const newOwner = await leaveGroup(params.id, userId, newOwnerId ?? null);
	if (newOwner) await publishToGroup(params.id, 'member.changed', newOwner);
	const payload = { userId, removed: true };
	await publishToGroup(params.id, 'member.changed', payload);
	publishToUsers([userId], { type: 'member.changed', groupId: params.id, payload });
	await onMemberLeft(params.id, userId);
	return noContent();
});
