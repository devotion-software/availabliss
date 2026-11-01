import { getGroup } from '#lib/client/groups';
import { toPageError } from '#lib/client/load';
import type { LayoutLoad } from './$types';

export const load: LayoutLoad = async ({ params, fetch, depends }) => {
	depends('app:group');
	const group = await getGroup(params.id, fetch).catch(toPageError);
	return { group };
};
