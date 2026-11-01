import { listUpcoming } from '#lib/client/events';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	// "Up next" is a nicety; the page still works without it.
	const upcoming = await listUpcoming(fetch).catch(() => []);
	return { upcoming };
};
