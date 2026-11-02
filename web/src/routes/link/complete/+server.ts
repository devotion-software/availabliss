import { redirect } from '@sveltejs/kit';
import { completeMerge, MERGE_COOKIE } from '#lib/server/accounts';
import type { RequestHandler } from './$types';

/**
 * Web merge, step 2: back from signing in to the other account. Merges it into the
 * account that started the merge (this browser's session moves with it).
 */
export const GET: RequestHandler = async ({ cookies, locals }) => {
	const code = cookies.get(MERGE_COOKIE);
	cookies.delete(MERGE_COOKIE, { path: '/' });
	let result: Awaited<ReturnType<typeof completeMerge>>;
	try {
		result = await completeMerge(
			code,
			locals.user && locals.session ? { userId: locals.user.id, sessionCreatedAt: new Date(locals.session.createdAt) } : null
		);
	} catch (err) {
		console.error('[link] merge failed', err);
		result = { ok: false, reason: 'merge_failed' };
	}
	if (!result.ok) redirect(303, `/app/settings?link_error=${result.reason}`);
	redirect(303, `/app/settings?${result.merged ? 'merged' : 'linked'}=${result.provider}`);
};
