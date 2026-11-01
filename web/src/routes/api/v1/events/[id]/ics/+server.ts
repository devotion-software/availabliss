import { authed } from '#lib/server/http';
import { getEvent } from '#lib/server/events';
import { getGroupRow } from '#lib/server/groups';
import { buildIcs } from '#lib/server/ics';

export const GET = authed<{ id: string }>(async ({ params, url, userId }) => {
	const event = await getEvent(params.id, userId);
	const group = await getGroupRow(event.groupId);
	const filename = event.title.replace(/[^\w\- ]+/g, '').trim().replace(/\s+/g, '-') || 'event';
	return new Response(buildIcs(event, group.name, url.hostname, group.requirement.timezone), {
		headers: {
			'content-type': 'text/calendar; charset=utf-8',
			'content-disposition': `attachment; filename="${filename}.ics"`
		}
	});
});
