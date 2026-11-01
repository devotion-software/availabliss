import { listMessages } from '#lib/client/chat';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params, fetch }) => {
	// A failed first page isn't fatal: the thread retries on the client.
	const initial = await listMessages(params.id, { limit: 50 }, fetch).catch(() => null);
	return { initial };
};
