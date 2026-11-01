import { api, apiUrl, type Fetch } from './api';
import type { CalendarConnection } from '#lib/types';

export type OAuthProvider = 'google' | 'microsoft';

export const listConnections = (fetch?: Fetch) =>
	api.get<{ connections: CalendarConnection[] }>('/calendars', { fetch }).then((r) => r.connections);

/** Full-page navigation target that starts the provider's consent flow. */
export const oauthStartUrl = (provider: OAuthProvider, redirect: string) =>
	apiUrl(`/calendars/oauth/${provider}/start`, { redirect });

export const connectCalDav = (body: { username: string; appPassword: string; serverUrl?: string }) =>
	api.post<CalendarConnection>('/calendars/caldav', body);

export const setCalendarBusy = (connectionId: string, calendarId: string, busy: boolean) =>
	api.patch<CalendarConnection>(`/calendars/${connectionId}/calendars/${calendarId}`, { busy });

export const syncConnection = (connectionId: string) => api.post<CalendarConnection>(`/calendars/${connectionId}/sync`);

export const removeConnection = (connectionId: string) => api.del(`/calendars/${connectionId}`);
