import { getProposal } from '#lib/client/proposals';
import { getGroup } from '#lib/client/groups';
import { toPageError } from '#lib/client/load';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params, fetch, depends }) => {
	depends('app:proposal');
	const proposal = await getProposal(params.id, fetch).catch(toPageError);
	const group = await getGroup(proposal.groupId, fetch).catch(toPageError);
	return { proposal, group };
};
