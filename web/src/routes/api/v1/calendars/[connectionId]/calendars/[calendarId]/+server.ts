import { authed, body, json } from '#lib/server/http';
import { setCalendarBusy } from '#lib/server/calendars';
import { calendarBusySchema } from '#lib/schemas';

export const PATCH = authed<{ connectionId: string; calendarId: string }>(async ({ event, params, userId }) => {
	const { busy } = await body(event, calendarBusySchema);
	return json(await setCalendarBusy(userId, params.connectionId, params.calendarId, busy));
});
