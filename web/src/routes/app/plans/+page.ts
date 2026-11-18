import { getMyPlans } from '#lib/client/plans';
import { toPageError } from '#lib/client/load';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, depends }) => {
	depends('app:plans');
	return { plans: await getMyPlans(fetch).catch(toPageError) };
};
