/**
 * Draft state for the event / proposal editors: wall-clock fields in the
 * user's zone, converted to the ISO instants the API expects.
 */
import { addDays, dayKeyIn, timeIn, zonedToIso } from './dates';

export interface EventDraft {
	title: string;
	startDate: string;
	startTime: string;
	endDate: string;
	endTime: string;
	allDay: boolean;
	location: string;
	note: string;
}

/** A draft covering `start`–`end`. All-day ends are exclusive midnights. */
export function draftFromRange(start: string, end: string, allDay: boolean, timeZone: string, title = ''): EventDraft {
	const lastInstant = allDay ? new Date(new Date(end).getTime() - 1).toISOString() : end;
	return {
		title,
		startDate: dayKeyIn(start, timeZone),
		startTime: allDay ? '18:00' : timeIn(start, timeZone),
		endDate: dayKeyIn(lastInstant, timeZone),
		endTime: allDay ? '21:00' : timeIn(end, timeZone),
		allDay,
		location: '',
		note: ''
	};
}

/** A blank draft for a day, defaulting to an evening. */
export function blankDraft(dayKey: string): EventDraft {
	return { title: '', startDate: dayKey, startTime: '18:00', endDate: dayKey, endTime: '21:00', allDay: false, location: '', note: '' };
}

export type DraftTimes = { start: string; end: string; allDay: boolean };

/** Converts the draft to API instants, or returns a readable problem. */
export function draftTimes(d: EventDraft, timeZone: string): DraftTimes | { error: string } {
	if (!d.startDate || !d.endDate) return { error: 'Choose a date' };
	if (d.allDay) {
		if (d.endDate < d.startDate) return { error: 'The end date is before the start' };
		return { start: zonedToIso(d.startDate, '00:00', timeZone), end: zonedToIso(addDays(d.endDate, 1), '00:00', timeZone), allDay: true };
	}
	if (!d.startTime || !d.endTime) return { error: 'Choose a start and end time' };
	const start = zonedToIso(d.startDate, d.startTime, timeZone);
	const end = zonedToIso(d.endDate, d.endTime, timeZone);
	if (end <= start) return { error: 'It ends before it starts' };
	return { start, end, allDay: false };
}

/** Optional text fields as the API wants them (empty → null). */
export const optional = (s: string) => s.trim() || null;
