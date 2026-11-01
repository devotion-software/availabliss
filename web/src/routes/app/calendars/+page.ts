import { listConnections } from '#lib/client/calendars';
import { toPageError } from '#lib/client/load';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => ({
	connections: await listConnections(fetch).catch(toPageError)
});
