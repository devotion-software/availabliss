/** "Add to calendar" helpers for the web: Google Calendar template links. */
import { addDays } from './dates';

export interface CalendarItem {
	title: string;
	start: string;
	end: string;
	allDay: boolean;
	location?: string | null;
	note?: string | null;
}

const compactUtc = (iso: string) =>
	new Date(iso)
		.toISOString()
		.replace(/[-:]/g, '')
		.replace(/\.\d{3}/, '');

/** Opens Google Calendar's "new event" form prefilled with the item. */
export function googleCalendarUrl(item: CalendarItem, dayKey: (iso: string) => string): string {
	let dates: string;
	if (item.allDay) {
		// All-day ranges use exclusive end dates.
		const first = dayKey(item.start);
		const lastInclusive = dayKey(new Date(new Date(item.end).getTime() - 1).toISOString());
		dates = `${first.replace(/-/g, '')}/${addDays(lastInclusive < first ? first : lastInclusive, 1).replace(/-/g, '')}`;
	} else {
		dates = `${compactUtc(item.start)}/${compactUtc(item.end)}`;
	}
	const params = new URLSearchParams({ action: 'TEMPLATE', text: item.title, dates });
	if (item.location) params.set('location', item.location);
	if (item.note) params.set('details', item.note);
	return `https://calendar.google.com/calendar/render?${params}`;
}

/** Events this browser has added to a calendar (downloaded the .ics, or opened in Google Calendar). */
const ADDED_KEY = 'calendar.addedEvents';
/** Keep the list from growing for ever: the most recent this many. */
const MAX_ADDED = 200;

function storage(): Storage | null {
	try {
		return typeof localStorage === 'undefined' ? null : localStorage;
	} catch {
		return null;
	}
}

function readAdded(store: Storage | null): string[] {
	try {
		const ids = JSON.parse(store?.getItem(ADDED_KEY) ?? '[]');
		return Array.isArray(ids) ? ids.filter((id): id is string => typeof id === 'string') : [];
	} catch {
		return [];
	}
}

/** Whether this browser added the event to a calendar before. False when storage is blocked. */
export function wasAddedToCalendar(eventId: string, store: Storage | null = storage()): boolean {
	return readAdded(store).includes(eventId);
}

/** Remembers that this browser added the event to a calendar. Does nothing when storage is blocked. */
export function rememberAddedToCalendar(eventId: string, store: Storage | null = storage()): void {
	const ids = [...readAdded(store).filter((id) => id !== eventId), eventId].slice(-MAX_ADDED);
	try {
		store?.setItem(ADDED_KEY, JSON.stringify(ids));
	} catch {
		// Not remembered: the button just shows again next time.
	}
}
