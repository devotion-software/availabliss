/**
 * How a calendar's changes can be found without fetching everything again:
 * - `cursor`: the provider lists events changed since a time (`changes`).
 * - `version`: the calendar carries a tag that changes whenever any event does.
 * - `none`: no way to tell, so busy time is re-read on a schedule.
 */
export type ChangeTracking = 'cursor' | 'version' | 'none';

export interface RemoteCalendar {
	externalId: string;
	name: string;
	color: string | null;
	changes: ChangeTracking;
	/** For `version` calendars: the current change tag. */
	version?: string | null;
}

export interface RemoteBusy {
	calendarExternalId: string;
	/** Opaque key of the event or recurring series this came from, if the provider gives ids. */
	eventKey?: string;
	start: Date;
	end: Date;
}

/** Events changed since a cursor: each key's busy time in the range, replacing what was stored (empty = gone). */
export type ChangedEvents = Map<string, RemoteBusy[]>;

export interface OAuthCredentials {
	accessToken: string;
	refreshToken: string | null;
	/** Epoch ms. */
	expiresAt: number;
}

export interface CaldavCredentials {
	serverUrl: string;
	username: string;
	password: string;
}

/** Thrown when stored credentials no longer work and the user must reconnect. */
export class ReauthRequired extends Error {}

/** What every server-side calendar provider implements. */
export interface CalendarAdapter<Creds> {
	listCalendars(creds: Creds): Promise<RemoteCalendar[]>;
	/** Busy time overlapping [from, to) in these calendars. */
	busy(creds: Creds, calendars: RemoteCalendar[], from: Date, to: Date, timeZone: string): Promise<RemoteBusy[]>;
	/**
	 * For `cursor` calendars: events changed since `since`, with their busy time in
	 * [from, to). Null when the provider can't answer from that point (full fetch instead).
	 */
	changes?(creds: Creds, calendar: RemoteCalendar, since: Date, from: Date, to: Date, timeZone: string): Promise<ChangedEvents | null>;
	/** Returns fresh credentials when the access token is about to expire. */
	refresh?(creds: Creds): Promise<Creds | null>;
}
