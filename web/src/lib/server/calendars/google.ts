/**
 * Google Calendar. Calendars the user can read are synced by change: the first
 * fetch lists every occurrence in the range, then later syncs ask only for
 * events updated since the last one (`updatedMin`) and re-read the occurrences
 * of any recurring series that changed. Calendars shared as free/busy only use
 * the freeBusy API. Only start, end, transparency and the user's own response
 * are read; titles never are.
 */
import { createHash } from 'node:crypto';
import { zonedInstant } from '../availability/time';
import { bearerFetch, HttpError, refreshIfNeeded } from './oauth';
import type { CalendarAdapter, ChangedEvents, OAuthCredentials, PushChannel, RemoteBusy, RemoteCalendar } from './types';

const API = 'https://www.googleapis.com/calendar/v3';
/** freeBusy accepts at most 50 calendars and a bounded window per call. */
const MAX_CALENDARS = 50;
const FREEBUSY_WINDOW_MS = 60 * 24 * 60 * 60 * 1000;
const EVENT_FIELDS = 'id,status,transparency,start,end,recurringEventId,recurrence,attendees(self,responseStatus)';
const PAGE_FIELDS = `nextPageToken,timeZone,items(${EVENT_FIELDS})`;
/** Channels are asked to last this long (Google may grant less). */
const CHANNEL_TTL_SECONDS = 7 * 24 * 60 * 60;

interface CalendarListResponse {
	items: { id: string; summary: string; summaryOverride?: string; backgroundColor?: string; primary?: boolean; accessRole?: string }[];
}

interface FreeBusyResponse {
	calendars: Record<string, { busy: { start: string; end: string }[]; errors?: unknown[] }>;
}

interface EventTime {
	dateTime?: string;
	date?: string;
}

export interface GoogleEvent {
	id: string;
	status?: string;
	transparency?: string;
	start?: EventTime;
	end?: EventTime;
	recurringEventId?: string;
	recurrence?: string[];
	attendees?: { self?: boolean; responseStatus?: string }[];
}

interface EventsPage {
	items?: GoogleEvent[];
	nextPageToken?: string;
	timeZone?: string;
}

/** Stored instead of Google's id: rows can be matched to an event without keeping the id itself. */
export const eventKey = (id: string) => createHash('sha256').update(id).digest('base64url').slice(0, 22);

/** The series an event belongs to: its own id for single events and series masters. */
const seriesOf = (e: GoogleEvent) => e.recurringEventId ?? e.id;

const instant = (t: EventTime | undefined, timeZone: string) =>
	t?.dateTime ? Date.parse(t.dateTime) : t?.date ? zonedInstant(t.date, 0, timeZone) : NaN;

/** The busy span of one event, or null when it doesn't make the user busy. */
export function eventSpan(e: GoogleEvent, timeZone: string): { start: number; end: number } | null {
	if (e.status === 'cancelled' || e.transparency === 'transparent') return null;
	if (e.attendees?.some((a) => a.self && a.responseStatus === 'declined')) return null;
	const start = instant(e.start, timeZone);
	const end = instant(e.end, timeZone);
	return Number.isFinite(start) && Number.isFinite(end) && end > start ? { start, end } : null;
}

/** Every page of an events listing. Null when Google says the request can't be answered (410 Gone). */
async function allEvents(creds: OAuthCredentials, url: string, timeZone: string): Promise<{ items: GoogleEvent[]; timeZone: string } | null> {
	const items: GoogleEvent[] = [];
	let zone = timeZone;
	let pageToken: string | undefined;
	do {
		const page: EventsPage | null = await bearerFetch<EventsPage>(pageToken ? `${url}&pageToken=${encodeURIComponent(pageToken)}` : url, creds.accessToken).catch(
			(err) => {
				if (err instanceof HttpError && (err.status === 410 || err.status === 404)) return null;
				throw err;
			}
		);
		if (!page) return null;
		items.push(...(page.items ?? []));
		zone = page.timeZone ?? zone;
		pageToken = page.nextPageToken;
	} while (pageToken);
	return { items, timeZone: zone };
}

const eventsUrl = (calendarId: string, params: Record<string, string>) =>
	`${API}/calendars/${encodeURIComponent(calendarId)}/events?${new URLSearchParams({ maxResults: '2500', fields: PAGE_FIELDS, ...params })}`;

function toBusy(calendarExternalId: string, events: GoogleEvent[], timeZone: string): RemoteBusy[] {
	return events.flatMap((e) => {
		const span = eventSpan(e, timeZone);
		return span ? [{ calendarExternalId, eventKey: eventKey(seriesOf(e)), start: new Date(span.start), end: new Date(span.end) }] : [];
	});
}

/** Every occurrence overlapping [from, to), expanded by Google. */
async function occurrences(creds: OAuthCredentials, calendarId: string, from: Date, to: Date, timeZone: string): Promise<RemoteBusy[]> {
	const url = eventsUrl(calendarId, { singleEvents: 'true', timeMin: from.toISOString(), timeMax: to.toISOString() });
	const result = await allEvents(creds, url, timeZone);
	return result ? toBusy(calendarId, result.items, result.timeZone) : [];
}

async function freeBusy(creds: OAuthCredentials, calendars: RemoteCalendar[], from: Date, to: Date): Promise<RemoteBusy[]> {
	const out: RemoteBusy[] = [];
	for (let start = from.getTime(); start < to.getTime(); start += FREEBUSY_WINDOW_MS) {
		const end = Math.min(start + FREEBUSY_WINDOW_MS, to.getTime());
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
}

export const google: CalendarAdapter<OAuthCredentials> = {
	async listCalendars(creds) {
		const data = await bearerFetch<CalendarListResponse>(`${API}/users/me/calendarList?minAccessRole=freeBusyReader`, creds.accessToken);
		return data.items.map((c) => ({
			externalId: c.id,
			name: c.summaryOverride ?? c.summary,
			color: c.backgroundColor ?? null,
			// Events can't be listed in a calendar shared as free/busy only.
			changes: c.accessRole === 'freeBusyReader' ? 'none' : 'cursor'
		}));
	},

	async busy(creds, calendars, from, to, timeZone) {
		const out = await freeBusy(creds, calendars.filter((c) => c.changes !== 'cursor'), from, to);
		for (const cal of calendars.filter((c) => c.changes === 'cursor')) out.push(...(await occurrences(creds, cal.externalId, from, to, timeZone)));
		return out;
	},

	async changes(creds, calendar, since, from, to, timeZone) {
		// Deleted events are always included when `updatedMin` is set.
		const url = eventsUrl(calendar.externalId, { updatedMin: since.toISOString(), showDeleted: 'true' });
		const changed = await allEvents(creds, url, timeZone);
		if (!changed) return null;

		const out: ChangedEvents = new Map();
		const series = new Set<string>();
		for (const e of changed.items) {
			// Any change to a recurring series (the rule, one occurrence, a deletion of one) re-reads its occurrences.
			if (e.recurrence || e.recurringEventId) series.add(seriesOf(e));
			else out.set(eventKey(e.id), toBusy(calendar.externalId, [e], changed.timeZone));
		}
		for (const id of series) {
			const url = `${API}/calendars/${encodeURIComponent(calendar.externalId)}/events/${encodeURIComponent(id)}/instances?${new URLSearchParams({
				maxResults: '2500',
				fields: PAGE_FIELDS,
				timeMin: from.toISOString(),
				timeMax: to.toISOString()
			})}`;
			// A deleted series answers 404/410: nothing left of it.
			const result = await allEvents(creds, url, changed.timeZone);
			out.set(eventKey(id), result ? toBusy(calendar.externalId, result.items, result.timeZone) : []);
		}
		return out;
	},

	async watch(creds, calendar, { id, address, token }): Promise<PushChannel | null> {
		// Only calendars whose events we can list can be watched.
		if (calendar.changes !== 'cursor') return null;
		const channel = await bearerFetch<{ id: string; resourceId: string; expiration?: string }>(
			`${API}/calendars/${encodeURIComponent(calendar.externalId)}/events/watch`,
			creds.accessToken,
			{
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ id, type: 'web_hook', address, token, params: { ttl: String(CHANNEL_TTL_SECONDS) } })
			}
		).catch((err) => {
			// Some calendars (other people's, public ones) refuse to be watched.
			if (err instanceof HttpError && err.status >= 400 && err.status < 500) return null;
			throw err;
		});
		if (!channel) return null;
		const expiresAt = new Date(channel.expiration ? Number(channel.expiration) : Date.now() + CHANNEL_TTL_SECONDS * 1000);
		return { id: channel.id, resourceId: channel.resourceId, expiresAt };
	},

	async unwatch(creds, { id, resourceId }) {
		await bearerFetch(`${API}/channels/stop`, creds.accessToken, {
			method: 'POST',
			headers: { 'content-type': 'application/json' },
			body: JSON.stringify({ id, resourceId })
		}).catch((err) => {
			if (!(err instanceof HttpError && err.status === 404)) throw err;
		});
	},

	refresh: (creds) => refreshIfNeeded('google', creds)
};

/** The primary calendar's id is the account's email address. */
export async function googleAccountLabel(creds: OAuthCredentials): Promise<string> {
	const data = await bearerFetch<CalendarListResponse>(`${API}/users/me/calendarList`, creds.accessToken);
	return data.items.find((c) => c.primary)?.id ?? 'Google Calendar';
}
