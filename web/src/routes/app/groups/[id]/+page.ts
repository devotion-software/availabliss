import { getAvailability } from '#lib/client/availability';
import { endOfMonth, startOfMonth, todayIn } from '#lib/client/dates';
import { toPageError } from '#lib/client/load';
import type { PageLoad } from './$types';

/** Opens on this month (or the first month of the search window, if later). */
export const load: PageLoad = async ({ parent, fetch, params, depends }) => {
	depends('app:availability');
	const { user, group } = await parent();
	const today = todayIn(user.timezone);
	const month = startOfMonth(group.requirement.range.from > today ? group.requirement.range.from : today);
	const availability = await getAvailability(params.id, month, endOfMonth(month), fetch).catch(toPageError);
	return { month, availability };
};
