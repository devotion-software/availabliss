import { getGroup } from '#lib/client/groups';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ url, fetch }) => {
	const id = url.searchParams.get('group');
	const group = id ? await getGroup(id, fetch).catch(() => null) : null;
	return { group };
};
