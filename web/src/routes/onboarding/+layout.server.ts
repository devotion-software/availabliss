import { redirect } from '@sveltejs/kit';
import { toPublicUser } from '#lib/public-user';
import { assignUsername } from '#lib/server/usernames';
import { groupIdsFor } from '#lib/server/groups';
import { detectRegion } from '#lib/server/holidays';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals, url, request }) => {
	if (!locals.user) redirect(302, `/signin?next=${encodeURIComponent(url.pathname)}`);
	if (locals.user.onboarded) redirect(302, '/app');
	const [username, groupIds] = await Promise.all([locals.user.username ?? assignUsername(locals.user.id), groupIdsFor(locals.user.id)]);
	locals.user.username = username;
	// Someone who joined from an invite is already in a group: the wizard stops after calendars.
	const inGroup = groupIds.length > 0;
	// Where they seem to be, from this request's headers, so the first step renders filled in.
	const detected = locals.user.country ? null : detectRegion(request.headers);
	return { user: toPublicUser(locals.user), steps: inGroup ? 2 : 4, detected };
};
