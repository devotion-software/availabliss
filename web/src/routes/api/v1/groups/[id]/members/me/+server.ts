import { authed, body, json } from '#lib/server/http';
import { setMemberDisplayName } from '#lib/server/groups';
import { publishToGroup } from '#lib/server/realtime';
import { memberNameSchema } from '#lib/schemas';

/** The name you go by in this group: `{ displayName }` sets it, null or "" goes back to your account name. */
export const PATCH = authed<{ id: string }>(async ({ event, params, userId }) => {
	const { displayName } = await body(event, memberNameSchema);
	const member = await setMemberDisplayName(params.id, userId, displayName);
	await publishToGroup(params.id, 'member.changed', member);
	return json(member);
});
