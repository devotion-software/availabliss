import { redirect } from '@sveltejs/kit';
import { safeRedirectPath } from '#lib/client/safe-path';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = ({ locals, url }) => {
	const next = safeRedirectPath(url.searchParams.get('next')) ?? '/app';
	if (locals.user) redirect(302, next);
	return { next };
};
