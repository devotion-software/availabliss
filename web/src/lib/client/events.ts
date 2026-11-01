import { api, apiUrl, type Fetch } from './api';
import type { GroupEvent } from '#lib/types';

export interface EventInput {
	title: string;
	start: string;
	end: string;
	allDay?: boolean;
	location?: string | null;
	note?: string | null;
}

export type UpcomingEvent = GroupEvent & { groupName: string };

export const listEvents = (groupId: string, from: string, to: string, fetch?: Fetch) =>
	api.get<{ events: GroupEvent[] }>(`/groups/${groupId}/events`, { fetch, query: { from, to } }).then((r) => r.events);
export const createEvent = (groupId: string, body: EventInput) => api.post<GroupEvent>(`/groups/${groupId}/events`, body);
export const updateEvent = (id: string, patch: Partial<EventInput>) => api.patch<GroupEvent>(`/events/${id}`, patch);
export const deleteEvent = (id: string) => api.del(`/events/${id}`);
export const listUpcoming = (fetch?: Fetch) =>
	api.get<{ events: UpcomingEvent[] }>('/me/upcoming', { fetch }).then((r) => r.events);

/** Download link for the event's .ics file ("Add to calendar" on the web). */
export const eventIcsUrl = (id: string) => apiUrl(`/events/${id}/ics`);

export const getEvent = (id: string, fetch?: Fetch) => api.get<GroupEvent>(`/events/${id}`, { fetch });

const eventCache = new Map<string, Promise<GroupEvent>>();
/** `getEvent`, cached per id for the tab's lifetime (chat cards re-render often). Failures aren't cached. */
export function getEventCached(id: string): Promise<GroupEvent> {
	let p = eventCache.get(id);
	if (!p) {
		p = getEvent(id).catch((err) => {
			eventCache.delete(id);
			throw err;
		});
		eventCache.set(id, p);
	}
	return p;
}
