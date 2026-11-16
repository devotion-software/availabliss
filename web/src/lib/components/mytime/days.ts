/**
 * "My time" day list: what each day's row says ("Free from 18:00"), what a
 * screen reader hears, the week headings and the "Block time" presets.
 */
import type { BusyInterval } from '#lib/types';
import { addDays, startOfWeek } from '#lib/client/dates';
import { dayNumber, formatDayRange, weekdayName } from '#lib/client/format';
import { clock, DAY_MINUTES } from './parts';

/** The row's strip and summary cover the waking day, 06:00 – 24:00. */
export const STRIP_FROM = 6 * 60;
export const STRIP_TO = DAY_MINUTES;
/** Free gaps shorter than this don't count as free time worth mentioning. */
export const MIN_FREE_MINUTES = 30;
/** Busy is fetched this many days at a time (the server allows up to 120). */
export const CHUNK_DAYS = 28;

export interface Range {
	from: number;
	to: number;
}

/** A timed piece of busy time on one day (minutes since midnight). */
export interface DayPiece extends Range {
	interval: BusyInterval;
}

/** Busy time that counts: everything except calendar events you've marked not busy. */
export const counts = (i: BusyInterval) => !i.ignoreId;

/** Sorted, merged ranges. */
export function mergeRanges(ranges: readonly Range[]): Range[] {
	const sorted = ranges.filter((r) => r.to > r.from).sort((a, b) => a.from - b.from);
	const out: Range[] = [];
	for (const r of sorted) {
		const last = out[out.length - 1];
		if (last && r.from <= last.to) last.to = Math.max(last.to, r.to);
		else out.push({ from: r.from, to: r.to });
	}
	return out;
}

/** Busy ranges for a day that count, merged; a counting all-day item makes the whole day busy. */
export function busyRanges(timed: readonly DayPiece[], allDay: readonly BusyInterval[]): Range[] {
	if (allDay.some(counts)) return [{ from: 0, to: DAY_MINUTES }];
	return mergeRanges(timed.filter((p) => counts(p.interval)));
}

/** Free gaps within [from, to] around `busy` (merged ranges). */
export function freeGaps(busy: readonly Range[], from = STRIP_FROM, to = STRIP_TO): Range[] {
	const gaps: Range[] = [];
	let at = from;
	for (const b of busy) {
		if (b.to <= at) continue;
		if (b.from >= to) break;
		if (b.from > at) gaps.push({ from: at, to: b.from });
		at = Math.max(at, b.to);
	}
	if (at < to) gaps.push({ from: at, to });
	return gaps;
}

/**
 * One line for a day row: "Free all day", "Busy all day", "Free from 18:00",
 * "Free until 09:00" or "Free 12:00 – 14:00" (the longest free stretch, 06:00 – 24:00).
 */
export function freeSummary(busy: readonly Range[]): string {
	const gaps = freeGaps(busy);
	if (gaps.length === 1 && gaps[0].from === STRIP_FROM && gaps[0].to === STRIP_TO) return 'Free all day';
	let best: Range | null = null;
	// Ties go to the later stretch (evenings matter most), as on iOS and Android.
	for (const g of gaps) if (g.to - g.from >= MIN_FREE_MINUTES && (!best || g.to - g.from >= best.to - best.from)) best = g;
	if (!best) return 'Busy all day';
	if (best.to === STRIP_TO) return `Free from ${clock(best.from)}`;
	if (best.from === STRIP_FROM) return `Free until ${clock(best.to)}`;
	return `Free ${clock(best.from)} – ${clock(best.to)}`;
}

/** Your own block notes that day ("Choir"), once each, in time order. Calendar labels are calendar names, not events, so they're left out. */
export function dayNames(timed: readonly DayPiece[], allDay: readonly BusyInterval[]): string[] {
	const items = [...allDay, ...[...timed].sort((a, b) => a.from - b.from).map((p) => p.interval)];
	const names = items.filter((i) => counts(i) && i.source === 'manual' && i.label).map((i) => i.label!.trim());
	return [...new Set(names.filter(Boolean))];
}

const joinAnd = (parts: string[]) => (parts.length < 2 ? parts.join('') : `${parts.slice(0, -1).join(', ')} and ${parts[parts.length - 1]}`);

/** What a screen reader hears for a row: "Monday 17 November, busy 09:00 to 17:00 and 19:00 to 21:30, free from 21:30". */
export function describeDay(day: string, busy: readonly Range[], names: readonly string[] = []): string {
	const date = longDate(day);
	const summary = freeSummary(busy);
	const parts = [date];
	if (busy.length === 1 && busy[0].from === 0 && busy[0].to === DAY_MINUTES) parts.push('busy all day');
	else {
		if (busy.length) parts.push(`busy ${joinAnd(busy.map((b) => `${clock(b.from)} to ${clock(b.to)}`))}`);
		if (summary !== 'Busy all day') parts.push(summary.charAt(0).toLowerCase() + summary.slice(1).replace(' – ', ' to '));
	}
	if (names.length) parts.push(names.join(', '));
	return parts.join(', ');
}

const monthLong = (day: string) => new Intl.DateTimeFormat('en-GB', { month: 'long', timeZone: 'UTC' }).format(new Date(`${day}T00:00:00Z`));

/** "Monday 17 November". */
export const longDate = (day: string) => `${weekdayName(day, 'long')} ${dayNumber(day)} ${monthLong(day)}`;

/** "This week", "Next week", then "23 – 29 Nov". */
export function weekHeading(weekStart: string, today: string): string {
	const thisWeek = startOfWeek(today);
	if (weekStart === thisWeek) return 'This week';
	if (weekStart === addDays(thisWeek, 7)) return 'Next week';
	return formatDayRange(weekStart, addDays(weekStart, 6), false);
}

/** Days grouped under week headings, the first week starting today. */
export function groupWeeks(days: readonly string[], today: string): { start: string; heading: string; days: string[] }[] {
	const out: { start: string; heading: string; days: string[] }[] = [];
	for (const day of days) {
		const start = startOfWeek(day);
		let last = out[out.length - 1];
		if (!last || last.start !== start) out.push((last = { start, heading: weekHeading(start, today), days: [] }));
		last.days.push(day);
	}
	return out;
}

/** "This evening": 18:00 – 23:59 today, or tomorrow once it's past 18:00. */
export function eveningPreset(today: string, nowMinutes: number): { day: string; range: Range } {
	return { day: nowMinutes >= 18 * 60 ? addDays(today, 1) : today, range: { from: 18 * 60, to: 23 * 60 + 59 } };
}
