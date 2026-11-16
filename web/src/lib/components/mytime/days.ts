/**
 * "My time" day summaries: how free each day is (the month's heat map), what a
 * day says ("Free from 18:00"), what a screen reader hears, the agenda's rows,
 * the week headings and the "Block time" presets.
 */
import type { BusyInterval, ManualBlock } from '#lib/types';
import { addDays, endOfMonth, startOfWeek, weekdayOf } from '#lib/client/dates';
import { dayNumber, formatDayRange, weekdayName } from '#lib/client/format';
import { clock, DAY_MINUTES } from './parts';
import { repeatTag } from './repeat';

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

/** The longest free stretch of at least half an hour, 06:00 – 24:00 (ties go to the later one: evenings matter most, as on iOS and Android). */
export function longestFree(busy: readonly Range[]): Range | null {
	let best: Range | null = null;
	for (const g of freeGaps(busy)) if (g.to - g.from >= MIN_FREE_MINUTES && (!best || g.to - g.from >= best.to - best.from)) best = g;
	return best;
}

/** The free stretch spans the whole waking day. */
export const isWholeDay = (r: Range) => r.from === STRIP_FROM && r.to === STRIP_TO;

/**
 * One line for a day: "Free all day", "Busy all day", "Free from 18:00",
 * "Free until 09:00" or "Free 12:00 – 14:00" (the longest free stretch, 06:00 – 24:00).
 */
export function freeSummary(busy: readonly Range[]): string {
	const best = longestFree(busy);
	if (!best) return 'Busy all day';
	if (isWholeDay(best)) return 'Free all day';
	if (best.to === STRIP_TO) return `Free from ${clock(best.from)}`;
	if (best.from === STRIP_FROM) return `Free until ${clock(best.to)}`;
	return `Free ${clock(best.from)} – ${clock(best.to)}`;
}

/** Free minutes within 06:00 – 24:00 (1080 when nothing's busy). */
export function freeMinutes(busy: readonly Range[]): number {
	let taken = 0;
	for (const b of mergeRanges(busy)) taken += Math.max(0, Math.min(b.to, STRIP_TO) - Math.max(b.from, STRIP_FROM));
	return STRIP_TO - STRIP_FROM - taken;
}

export type FreeLevel = 0 | 1 | 2 | 3;

/** How free a day is, for the month's heat map: 3 = 12h+ free, 2 = 6h+, 1 = half an hour+, 0 = busy all day. */
export function freeLevel(busy: readonly Range[]): FreeLevel {
	const free = freeMinutes(busy);
	if (free >= 12 * 60) return 3;
	if (free >= 6 * 60) return 2;
	if (free >= MIN_FREE_MINUTES) return 1;
	return 0;
}

/** What a busy item is called: your block's note or your calendar event's name (calendar `label` is the calendar's name), else null. */
export function busyName(i: BusyInterval): string | null {
	return ((i.source === 'manual' ? i.label : i.title) ?? '').trim() || null;
}

/** The names of what's on that day ("Choir", "Lunch with Mum"), once each, in time order. */
export function dayNames(timed: readonly DayPiece[], allDay: readonly BusyInterval[]): string[] {
	const items = [...allDay, ...[...timed].sort((a, b) => a.from - b.from).map((p) => p.interval)];
	const names = items.filter(counts).map(busyName);
	return [...new Set(names.filter((n): n is string => !!n))];
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

/** What a screen reader hears for a day in the month: "Monday 23 November, today, free from 17:30". */
export function dayCellLabel(day: string, today: string, busy: readonly Range[] | null): string {
	const parts = [longDate(day)];
	if (day === today) parts.push('today');
	if (busy) {
		const summary = freeSummary(busy);
		parts.push(summary.charAt(0).toLowerCase() + summary.slice(1).replace(' – ', ' to '));
	}
	return parts.join(', ');
}

/** The week view's line under the strip: "Today · Free from 17:30 · Choir". */
export function dayLine(day: string, today: string, busy: readonly Range[], names: readonly string[] = []): string {
	return [day === today ? 'Today' : longDate(day), freeSummary(busy), ...names].join(' · ');
}

/** What's on a day, for the dots on the week strip and month: one of your blocks, calendar busy. */
export function dayMarks(timed: readonly DayPiece[], allDay: readonly BusyInterval[]): { mine: boolean; calendar: boolean } {
	const items = [...allDay, ...timed.map((p) => p.interval)].filter(counts);
	return { mine: items.some((i) => i.source === 'manual'), calendar: items.some((i) => i.source === 'calendar') };
}

/** The month as cells, Monday first: blanks (null) before the 1st, then every day. */
export function monthCells(month: string): (string | null)[] {
	const first = month.slice(0, 8) + '01';
	const cells: (string | null)[] = Array.from({ length: weekdayOf(first) }, () => null);
	for (let d = first, last = endOfMonth(first); d <= last; d = addDays(d, 1)) cells.push(d);
	return cells;
}

/** One row in the agenda: a busy item that day. */
export interface AgendaItem {
	interval: BusyInterval;
	/** Null for all day. */
	range: Range | null;
	title: string;
	tag: string;
}

/** "Daily", "Weekly", "Every other week", "Monthly", …, "Your block" or "Calendar". */
export function sourceTag(interval: BusyInterval, blocks: readonly ManualBlock[]): string {
	if (interval.source === 'calendar') return 'Calendar';
	return repeatTag(blocks.find((b) => b.id === interval.blockId)?.repeat);
}

/** A day's busy items for the agenda: all day first, then by start; events marked not busy left out. */
export function agendaItems(timed: readonly DayPiece[], allDay: readonly BusyInterval[], blocks: readonly ManualBlock[]): AgendaItem[] {
	const item = (interval: BusyInterval, range: Range | null): AgendaItem => ({
		interval,
		range,
		title: busyName(interval) ?? 'Busy',
		tag: sourceTag(interval, blocks)
	});
	return [
		...allDay.filter(counts).map((i) => item(i, null)),
		...timed
			.filter((p) => counts(p.interval))
			.sort((a, b) => a.from - b.from || a.to - b.to)
			.map((p) => item(p.interval, { from: p.from, to: p.to }))
	];
}

/** `day`, kept between `first` and `last`. */
export const clampDay = (day: string, first: string, last: string) => (day < first ? first : day > last ? last : day);

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
