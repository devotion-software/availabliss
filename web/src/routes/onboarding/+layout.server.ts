import { redirect } from '@sveltejs/kit';
import { toPublicUser } from '#lib/public-user';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = ({ locals, url }) => {
	if (!locals.user) redirect(302, `/signin?next=${encodeURIComponent(url.pathname)}`);
	if (locals.user.onboarded) redirect(302, '/app');
	return { user: toPublicUser(locals.user) };
};
