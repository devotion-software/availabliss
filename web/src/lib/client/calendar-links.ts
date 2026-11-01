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

const compactUtc = (iso: string) => new Date(iso).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');

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
