import { authed, noContent } from '#lib/server/http';
import { declineInvite } from '#lib/server/groups';

/** Turns down an invite sent to you in Activity (by username or as one of a group's people). */
export const POST = authed<{ token: string }>(async ({ params, userId }) => {
	await declineInvite(params.token, userId);
	return noContent();
});
