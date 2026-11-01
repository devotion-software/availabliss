/** Google Calendar: calendar list + the freeBusy API (returns busy ranges only, never event details). */
import { bearerFetch, refreshIfNeeded } from './oauth';
import type { CalendarAdapter, OAuthCredentials, RemoteBusy } from './types';

const API = 'https://www.googleapis.com/calendar/v3';
/** freeBusy accepts at most 50 calendars and a bounded window per call. */
const MAX_CALENDARS = 50;
const WINDOW_MS = 60 * 24 * 60 * 60 * 1000;

interface CalendarListResponse {
	items: { id: string; summary: string; summaryOverride?: string; backgroundColor?: string; primary?: boolean }[];
}

interface FreeBusyResponse {
	calendars: Record<string, { busy: { start: string; end: string }[]; errors?: unknown[] }>;
}

export const google: CalendarAdapter<OAuthCredentials> = {
	async listCalendars(creds) {
		const data = await bearerFetch<CalendarListResponse>(`${API}/users/me/calendarList?minAccessRole=freeBusyReader`, creds.accessToken);
		return data.items.map((c) => ({ externalId: c.id, name: c.summaryOverride ?? c.summary, color: c.backgroundColor ?? null }));
	},

	async busy(creds, calendars, from, to) {
		const out: RemoteBusy[] = [];
		for (let start = from.getTime(); start < to.getTime(); start += WINDOW_MS) {
			const end = Math.min(start + WINDOW_MS, to.getTime());
			for (let i = 0; i < calendars.length; i += MAX_CALENDARS) {
				const batch = calendars.slice(i, i + MAX_CALENDARS);
				const data = await bearerFetch<FreeBusyResponse>(`${API}/freeBusy`, creds.accessToken, {
					method: 'POST',
					headers: { 'content-type': 'application/json' },
					body: JSON.stringify({
						timeMin: new Date(start).toISOString(),
						timeMax: new Date(end).toISOString(),
						items: batch.map((c) => ({ id: c.externalId }))
					})
				});
				for (const [id, cal] of Object.entries(data.calendars)) {
					for (const b of cal.busy) out.push({ calendarExternalId: id, start: new Date(b.start), end: new Date(b.end) });
				}
			}
		}
		return out;
	},

	refresh: (creds) => refreshIfNeeded('google', creds)
};

/** The primary calendar's id is the account's email address. */
export async function googleAccountLabel(creds: OAuthCredentials): Promise<string> {
	const data = await bearerFetch<CalendarListResponse>(`${API}/users/me/calendarList`, creds.accessToken);
	return data.items.find((c) => c.primary)?.id ?? 'Google Calendar';
}
