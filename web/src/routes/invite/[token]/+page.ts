import { getInvite } from '#lib/client/groups';
import { ApiError } from '#lib/client/api';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params, fetch }) => {
	try {
		return { token: params.token, invite: await getInvite(params.token, fetch), problem: null };
	} catch (err) {
		// Expired or unknown invites get a friendly page rather than an error screen.
		const problem = err instanceof ApiError && (err.status === 404 || err.status === 410) ? 'gone' : 'error';
		return { token: params.token, invite: null, problem };
	}
};
