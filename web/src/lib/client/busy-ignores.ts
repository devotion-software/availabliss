import { api } from './api';

/** Marks a calendar event not busy: that occurrence, or (`series`) every occurrence. */
export const ignoreBusy = (body: { calendarId: string; eventKey: string; start: string; end: string; series: boolean }) =>
	api.post<{ id: string }>('/me/busy/ignores', body);

/** The event counts as busy again. */
export const unignoreBusy = (id: string) => api.del(`/me/busy/ignores/${id}`);
