/**
 * Outlook / Microsoft 365 via Microsoft Graph calendarView (only start, end and
 * show-as are read). Graph's change feed only covers the primary calendar, so
 * these calendars are re-read on a schedule (`changes: 'none'`).
 */
import { bearerFetch, HttpError, refreshIfNeeded } from './oauth';
import type { CalendarAdapter, OAuthCredentials, PushChannel, RemoteBusy } from './types';

const GRAPH = 'https://graph.microsoft.com/v1.0';
/** Graph lets event subscriptions last at most 4230 minutes; stay under it. */
const SUBSCRIPTION_MINUTES = 4200;

interface CalendarsResponse {
	value: { id: string; name: string; hexColor?: string }[];
}

interface CalendarViewResponse {
	value: { start: { dateTime: string }; end: { dateTime: string }; showAs: string; isCancelled: boolean }[];
	'@odata.nextLink'?: string;
}

/** Graph returns UTC wall-clock strings without a zone when asked for UTC. */
const utc = (dateTime: string) => new Date(/[zZ]|[+-]\d\d:\d\d$/.test(dateTime) ? dateTime : `${dateTime}Z`);

export const microsoft: CalendarAdapter<OAuthCredentials> = {
	async listCalendars(creds) {
		const data = await bearerFetch<CalendarsResponse>(`${GRAPH}/me/calendars?$select=id,name,hexColor`, creds.accessToken);
		return data.value.map((c) => ({ externalId: c.id, name: c.name, color: c.hexColor || null, changes: 'none' as const }));
	},

	async busy(creds, calendars, from, to) {
		const out: RemoteBusy[] = [];
		for (const cal of calendars) {
			const params = new URLSearchParams({
				startDateTime: from.toISOString(),
				endDateTime: to.toISOString(),
				$select: 'start,end,showAs,isCancelled',
				$top: '500'
			});
			let next: string | undefined = `${GRAPH}/me/calendars/${encodeURIComponent(cal.externalId)}/calendarView?${params}`;
			while (next) {
				const page: CalendarViewResponse = await bearerFetch<CalendarViewResponse>(next, creds.accessToken, {
					headers: { Prefer: 'outlook.timezone="UTC"' }
				});
				for (const e of page.value) {
					if (e.isCancelled || e.showAs === 'free' || e.showAs === 'workingElsewhere') continue;
					out.push({ calendarExternalId: cal.externalId, start: utc(e.start.dateTime), end: utc(e.end.dateTime) });
				}
				next = page['@odata.nextLink'];
			}
		}
		return out;
	},

	async watch(creds, calendar, { address, token }): Promise<PushChannel> {
		// Graph checks `address` answers its validation request before replying.
		const sub = await bearerFetch<{ id: string; expirationDateTime: string }>(`${GRAPH}/subscriptions`, creds.accessToken, {
			method: 'POST',
			headers: { 'content-type': 'application/json' },
			body: JSON.stringify({
				changeType: 'created,updated,deleted',
				notificationUrl: address,
				resource: `me/calendars/${calendar.externalId}/events`,
				expirationDateTime: new Date(Date.now() + SUBSCRIPTION_MINUTES * 60 * 1000).toISOString(),
				clientState: token
			})
		});
		return { id: sub.id, resourceId: null, expiresAt: new Date(sub.expirationDateTime) };
	},

	async unwatch(creds, { id }) {
		await bearerFetch(`${GRAPH}/subscriptions/${encodeURIComponent(id)}`, creds.accessToken, { method: 'DELETE' }).catch((err) => {
			if (!(err instanceof HttpError && err.status === 404)) throw err;
		});
	},

	async changeIsFar(creds, eventId, nearEnd) {
		// A deleted event can't be looked up, so wherever it was is re-read.
		if (!eventId) return true;
		const event = await bearerFetch<{ type?: string; start?: { dateTime: string } }>(
			`${GRAPH}/me/events/${encodeURIComponent(eventId)}?$select=start,type`,
			creds.accessToken,
			{ headers: { Prefer: 'outlook.timezone="UTC"' } }
		).catch((err) => {
			if (err instanceof HttpError && err.status === 404) return null;
			throw err;
		});
		if (!event?.start) return true;
		// A recurring series can reach any distance ahead.
		return event.type === 'seriesMaster' || utc(event.start.dateTime) >= nearEnd;
	},

	refresh: (creds) => refreshIfNeeded('microsoft', creds)
};

export async function microsoftAccountLabel(creds: OAuthCredentials): Promise<string> {
	const me = await bearerFetch<{ mail: string | null; userPrincipalName: string }>(`${GRAPH}/me?$select=mail,userPrincipalName`, creds.accessToken);
	return me.mail ?? me.userPrincipalName;
}
