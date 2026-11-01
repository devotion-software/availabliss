/** Pure helpers shared by the calendar views. */
import type { Availability, AvailabilityDay, Member, Slot, Weekday } from '#lib/types';
import { addDays, endOfMonth, startOfMonth, startOfWeek, weekdayOf } from '#lib/client/dates';
import { firstName, listNames } from '#lib/client/format';

/** The weekday columns to draw (all seven when the server gives none). */
export const visibleColumns = (a: Pick<Availability, 'visibleWeekdays'> | null): Weekday[] =>
	a?.visibleWeekdays.length ? [...a.visibleWeekdays].sort((x, y) => x - y) : [0, 1, 2, 3, 4, 5, 6];

/** Month rows: one per week touching the month, each holding the date for every visible column (or null outside the month). */
export function monthRows(month: string, columns: readonly Weekday[]): (string | null)[][] {
	const first = startOfMonth(month);
	const last = endOfMonth(month);
	const rows: (string | null)[][] = [];
	for (let week = startOfWeek(first); week <= last; week = addDays(week, 7)) {
		const row = columns.map((c) => {
			const d = addDays(week, c);
			return d >= first && d <= last ? d : null;
		});
		if (row.some(Boolean)) rows.push(row);
	}
	return rows;
}

/** The visible dates in the week containing `date`. */
export const weekDates = (date: string, columns: readonly Weekday[]) => columns.map((c) => addDays(startOfWeek(date), c));

/** The slot covering a date (by its calendar days in the user's zone). */
export function slotOn(slots: readonly Slot[], date: string, dayKey: (iso: string) => string): Slot | null {
	return (
		slots.find((s) => {
			const last = dayKey(new Date(new Date(s.end).getTime() - 1).toISOString());
			return dayKey(s.start) <= date && date <= last;
		}) ?? null
	);
}

/** "Priya", "Priya and Tom", "3 people" for busy notes. */
export function busyNames(ids: readonly string[], members: Map<string, Member>, short = false): string {
	const names = ids.map((id) => firstName(members.get(id)?.name ?? 'Someone'));
	if (short && names.length > 2) return `${names.length} people`;
	return listNames(names, 2);
}

export const dayMap = (days: readonly AvailabilityDay[]) => new Map(days.map((d) => [d.date, d]));

export { weekdayOf };
