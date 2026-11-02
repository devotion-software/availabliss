import { authed, body, noContent } from '#lib/server/http';
import { createMergeIntent, MERGE_COOKIE, mergeCookieOptions } from '#lib/server/accounts';
import { mergeIntentSchema } from '#lib/schemas';

/**
 * Web: you confirmed merging the account a sign-in already belongs to. Remembers
 * (in this browser) which account to merge it into; the client then signs in with
 * the provider and lands on /link/complete.
 */
export const POST = authed(async ({ event, userId }) => {
	const { provider } = await body(event, mergeIntentSchema);
	event.cookies.set(MERGE_COOKIE, await createMergeIntent(userId, provider), mergeCookieOptions);
	return noContent();
});
