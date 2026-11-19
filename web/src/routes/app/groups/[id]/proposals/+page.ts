import { listProposals } from '#lib/client/proposals';
import { listEvents } from '#lib/client/events';
import { toPageError } from '#lib/client/load';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params, fetch, depends }) => {
	depends('app:proposals');
	// Events from the start of today: one that's under way still shows.
	const today = new Date();
	today.setHours(0, 0, 0, 0);
	const [proposals, events] = await Promise.all([
		listProposals(params.id, undefined, fetch).catch(toPageError),
		listEvents(params.id, today.toISOString(), undefined, fetch).catch(toPageError)
	]);
	return { proposals, events: events.sort((a, b) => a.start.localeCompare(b.start)) };
};
