import { authed, noContent } from '#lib/server/http';
import { revokeInvite } from '#lib/server/groups';

/** Revokes an invite: its link stops working (admins only). */
export const DELETE = authed<{ id: string; inviteId: string }>(async ({ params, userId }) => {
	await revokeInvite(params.id, params.inviteId, userId);
	return noContent();
});
