/** Pure helpers shared by the calendar views. */
import type { Availability, AvailabilityDay, Member, Slot, Weekday } from '#lib/types';
import { addDays, addMonths, endOfMonth, startOfMonth, startOfWeek, weekdayOf } from '#lib/client/dates';
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

/** Where a run of days that works for everyone starts (not the middle of a weekend). */
export const startsFreeRun = (day: Pick<AvailabilityDay, 'status' | 'slotPosition'>): boolean =>
	day.status === 'match' && day.slotPosition !== 'middle' && day.slotPosition !== 'end';

/**
 * The first (forward) or last (back) day in [from, to] where a run that works for everyone starts,
 * fetching a month at a time (`fetchMonth` takes the month's first day). Null when there's none,
 * or a month couldn't be fetched. A search range is a few months; `maxMonths` just keeps a very
 * long one from fetching for ever.
 */
export async function findFreeDay(
	fetchMonth: (month: string) => Promise<Pick<Availability, 'days'>>,
	from: string,
	to: string,
	forward: boolean,
	maxMonths = 24
): Promise<string | null> {
	if (to < from) return null;
	let month = startOfMonth(forward ? from : to);
	for (let i = 0; i < maxMonths; i++) {
		if (forward ? month > to : endOfMonth(month) < from) return null;
		let loaded: Pick<Availability, 'days'>;
		try {
			loaded = await fetchMonth(month);
		} catch {
			return null;
		}
		const starts = loaded.days.filter((d) => d.date >= from && d.date <= to && startsFreeRun(d));
		const found = forward ? starts[0] : starts[starts.length - 1];
		if (found) return found.date;
		month = addMonths(month, forward ? 1 : -1);
	}
	return null;
}
