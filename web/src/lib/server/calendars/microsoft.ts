/** Outlook / Microsoft 365 via Microsoft Graph calendarView (only start, end and show-as are read). */
import { bearerFetch, refreshIfNeeded } from './oauth';
import type { CalendarAdapter, OAuthCredentials, RemoteBusy } from './types';

const GRAPH = 'https://graph.microsoft.com/v1.0';

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
		return data.value.map((c) => ({ externalId: c.id, name: c.name, color: c.hexColor || null }));
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

	refresh: (creds) => refreshIfNeeded('microsoft', creds)
};

export async function microsoftAccountLabel(creds: OAuthCredentials): Promise<string> {
	const me = await bearerFetch<{ mail: string | null; userPrincipalName: string }>(`${GRAPH}/me?$select=mail,userPrincipalName`, creds.accessToken);
	return me.mail ?? me.userPrincipalName;
}
