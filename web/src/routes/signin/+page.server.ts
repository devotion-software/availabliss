import { redirect } from '@sveltejs/kit';
import { safeRedirectPath } from '#lib/client/safe-path';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = ({ locals, url }) => {
	const next = safeRedirectPath(url.searchParams.get('next')) ?? '/app';
	if (locals.user) redirect(302, next);
	// Prefilled from an emailed invite ("Sign in to join").
	const email = url.searchParams.get('email')?.trim() ?? '';
	return { next, email: /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email) && email.length <= 255 ? email : '' };
};
