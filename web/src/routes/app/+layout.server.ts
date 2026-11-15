import { redirect } from '@sveltejs/kit';
import { toPublicUser } from '#lib/public-user';
import { assignUsername } from '#lib/server/usernames';
import type { LayoutServerLoad } from './$types';

/** Everything under /app needs a signed-in user who has finished onboarding. */
export const load: LayoutServerLoad = async ({ locals, url }) => {
	if (!locals.user) redirect(302, `/signin?next=${encodeURIComponent(url.pathname + url.search)}`);
	if (!locals.user.onboarded) redirect(302, '/onboarding');
	// Accounts from before usernames get one the first time they're seen.
	locals.user.username ??= await assignUsername(locals.user.id);
	return { user: toPublicUser(locals.user) };
};
