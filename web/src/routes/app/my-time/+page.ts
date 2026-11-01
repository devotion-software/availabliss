import { getMyBusy } from '#lib/client/me';
import { listBlocks } from '#lib/client/blocks';
import { addDays, startOfWeek, todayIn, zonedToIso } from '#lib/client/dates';
import { toPageError } from '#lib/client/load';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, url, parent, depends }) => {
	depends('app:my-time');
	const { user } = await parent();
	const today = todayIn(user.timezone);
	const asked = url.searchParams.get('week');
	const weekStart = startOfWeek(asked && /^\d{4}-\d{2}-\d{2}$/.test(asked) ? asked : today);
	// The server takes ISO instants for this range: the user's Monday 00:00 to next Monday 00:00.
	const [intervals, blocks] = await Promise.all([
		getMyBusy(zonedToIso(weekStart, '00:00', user.timezone), zonedToIso(addDays(weekStart, 7), '00:00', user.timezone), fetch),
		listBlocks(fetch)
	]).catch(toPageError);
	return { weekStart, today, intervals, blocks };
};
