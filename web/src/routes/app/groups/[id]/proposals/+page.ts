import { listProposals } from '#lib/client/proposals';
import { toPageError } from '#lib/client/load';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params, fetch, depends }) => {
	depends('app:proposals');
	const proposals = await listProposals(params.id, undefined, fetch).catch(toPageError);
	return { proposals };
};
