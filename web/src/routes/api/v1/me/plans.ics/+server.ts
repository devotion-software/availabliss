import { authed } from '#lib/server/http';
import { myPlans } from '#lib/server/events';
import { getGroupRow } from '#lib/server/groups';
import { buildFeed } from '#lib/server/ics';

/** "Add all to calendar": one .ics with every upcoming plan you're going to. */
export const GET = authed(async ({ url, userId }) => {
	const { going } = await myPlans(userId);
	const zones = new Map<string, string>();
	for (const plan of going) {
		if (!zones.has(plan.groupId)) zones.set(plan.groupId, (await getGroupRow(plan.groupId)).requirement.timezone);
	}
	const body = buildFeed(
		'Availabliss plans',
		going.map((event) => ({ event, groupName: event.groupName, timeZone: zones.get(event.groupId)!, updatedAt: new Date(event.createdAt) })),
		url.hostname
	);
	return new Response(body, {
		headers: { 'content-type': 'text/calendar; charset=utf-8', 'content-disposition': 'attachment; filename="availabliss-plans.ics"' }
	});
});
