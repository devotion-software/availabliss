import { getMyBusy } from '#lib/client/me';
import { listBlocks } from '#lib/client/blocks';
import { listConnections } from '#lib/client/calendars';
import { addDays, todayIn, zonedToIso } from '#lib/client/dates';
import { toPageError } from '#lib/client/load';
import { CHUNK_DAYS } from '#lib/components/mytime/days';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, parent, depends, untrack }) => {
	depends('app:my-time');
	// Untracked: the layout's groups refetch on live events shouldn't refetch this.
	const { user } = await untrack(() => parent());
	const today = todayIn(user.timezone);
	// The list starts today; the first few weeks come with the page, later ones as you scroll.
	const [intervals, blocks, connections] = await Promise.all([
		getMyBusy(zonedToIso(today, '00:00', user.timezone), zonedToIso(addDays(today, CHUNK_DAYS), '00:00', user.timezone), fetch),
		listBlocks(fetch),
		// Only for the "connect a calendar" hint, so a failure just hides it.
		listConnections(fetch).catch(() => null)
	]).catch(toPageError);
	return { today, intervals, blocks, hasCalendar: connections === null ? null : connections.length > 0 };
};
