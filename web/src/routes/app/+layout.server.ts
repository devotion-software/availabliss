import { redirect } from '@sveltejs/kit';
import { toPublicUser } from '#lib/public-user';
import type { LayoutServerLoad } from './$types';

/** Everything under /app needs a signed-in user who has finished onboarding. */
export const load: LayoutServerLoad = ({ locals, url }) => {
	if (!locals.user) redirect(302, `/signin?next=${encodeURIComponent(url.pathname + url.search)}`);
	if (!locals.user.onboarded) redirect(302, '/onboarding');
	return { user: toPublicUser(locals.user) };
};
