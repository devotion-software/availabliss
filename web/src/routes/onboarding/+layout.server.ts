import { redirect } from '@sveltejs/kit';
import { toPublicUser } from '#lib/public-user';
import { groupIdsFor } from '#lib/server/groups';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals, url }) => {
	if (!locals.user) redirect(302, `/signin?next=${encodeURIComponent(url.pathname)}`);
	if (locals.user.onboarded) redirect(302, '/app');
	// Someone who joined from an invite is already in a group: the wizard stops after calendars.
	const inGroup = (await groupIdsFor(locals.user.id)).length > 0;
	return { user: toPublicUser(locals.user), steps: inGroup ? 2 : 4 };
};
