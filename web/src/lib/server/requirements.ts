import type { Requirement } from '#lib/types';
import { addDays } from './availability/time';

/** Today's date in `timeZone`. */
export function today(timeZone: string, now = Date.now()): string {
	return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

/** New groups look for a weekend (Fri 18:00 → Sun) over the next three months. */
export function defaultRequirement(timeZone: string): Requirement {
	const from = today(timeZone);
	return {
		length: { type: 'span', startDay: 4, startTime: '18:00', endDay: 6, endTime: '23:59', weeksLater: 0 },
		window: null,
		days: [4, 5, 6],
		bankHolidays: false,
		schoolHolidaysOnly: false,
		schoolAuthority: null,
		customSchoolHolidays: [],
		range: { from, to: addDays(from, 91) },
		dates: [],
		timezone: timeZone
	};
}
