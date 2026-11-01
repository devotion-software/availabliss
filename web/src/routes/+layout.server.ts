import { toPublicUser } from '#lib/public-user';
import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = ({ locals }) => ({
	user: locals.user ? toPublicUser(locals.user) : null
});
