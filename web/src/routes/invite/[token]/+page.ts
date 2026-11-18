import { getInvite } from '#lib/client/groups';
import { ApiError } from '#lib/client/api';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params, fetch }) => {
	try {
		return { token: params.token, invite: await getInvite(params.token, fetch), problem: null };
	} catch (err) {
		// Expired or unknown invites get a friendly page rather than an error screen.
		const gone = err instanceof ApiError && (err.status === 404 || err.status === 410);
		// A link that's full says so ("This invite link has been used up").
		const problem = gone && err.message.includes('used up') ? 'used-up' : gone ? 'gone' : 'error';
		return { token: params.token, invite: null, problem };
	}
};
