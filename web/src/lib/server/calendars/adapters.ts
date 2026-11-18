/** The server-side calendar providers, and what the sync modules share about their connections. */
import type { CalendarProvider } from '#lib/types';
import type { calendarConnections } from '../db/schema';
import { caldav } from './caldav';
import { google } from './google';
import { microsoft } from './microsoft';
import type { CaldavCredentials, CalendarAdapter, OAuthCredentials } from './types';

export type ConnectionRow = typeof calendarConnections.$inferSelect;
export type ServerProvider = Exclude<CalendarProvider, 'device'>;

/** Exported so tests can stand in for a provider. */
export const adapters: Record<ServerProvider, CalendarAdapter<OAuthCredentials | CaldavCredentials>> = {
	google: google as CalendarAdapter<OAuthCredentials | CaldavCredentials>,
	microsoft: microsoft as CalendarAdapter<OAuthCredentials | CaldavCredentials>,
	caldav: caldav as CalendarAdapter<OAuthCredentials | CaldavCredentials>
};

/** Credentials are encrypted against the connection's id. */
export const credentialContext = (connectionId: string) => `calendar:${connectionId}`;
