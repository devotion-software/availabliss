import { authed, body, json } from '#lib/server/http';
import { linkWithIdToken } from '#lib/server/accounts';
import { linkAccountSchema } from '#lib/schemas';
import type { ApiErrorBody, MergeCandidate } from '#lib/types';

/**
 * Links Sign in with Apple / Google (native apps send the SDK's id token). If that
 * sign-in already has its own account, responds 409 with that account so the app
 * can ask before sending again with `merge: true`.
 */
export const POST = authed(async ({ event, userId }) => {
	const input = await body(event, linkAccountSchema);
	const result = await linkWithIdToken(userId, event.request.headers, input);
	if (!result.linked) {
		return json(
			{
				message: 'That sign-in already has its own Availabliss account',
				code: 'conflict',
				account: result.candidate
			} satisfies ApiErrorBody & { account: MergeCandidate },
			{ status: 409 }
		);
	}
	return json(result);
});
