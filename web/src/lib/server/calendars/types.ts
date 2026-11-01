export interface RemoteCalendar {
	externalId: string;
	name: string;
	color: string | null;
}

export interface RemoteBusy {
	calendarExternalId: string;
	start: Date;
	end: Date;
}

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
	busy(creds: Creds, calendars: RemoteCalendar[], from: Date, to: Date, timeZone: string): Promise<RemoteBusy[]>;
	/** Returns fresh credentials when the access token is about to expire. */
	refresh?(creds: Creds): Promise<Creds | null>;
}
