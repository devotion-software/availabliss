/**
 * Calendar-date and timezone helpers. Dates are `YYYY-MM-DD` strings, times are
 * `HH:MM`, instants are epoch milliseconds. Only Intl is used, so behaviour is
 * identical in Node, Bun and the browser.
 */
import type { Weekday } from '#lib/types';

export const MINUTE = 60_000;
export const HOUR = 60 * MINUTE;
export const DAY = 24 * HOUR;

const formatters = new Map<string, Intl.DateTimeFormat>();

function partsFormatter(timeZone: string): Intl.DateTimeFormat {
	let f = formatters.get(timeZone);
	if (!f) {
		f = new Intl.DateTimeFormat('en-GB', {
			timeZone,
			hourCycle: 'h23',
			year: 'numeric',
			month: '2-digit',
			day: '2-digit',
			hour: '2-digit',
			minute: '2-digit',
			second: '2-digit'
		});
		formatters.set(timeZone, f);
	}
	return f;
}

interface WallClock {
	year: number;
	month: number;
	day: number;
	hour: number;
	minute: number;
	second: number;
}

/** The wall-clock reading of `instant` in `timeZone`. */
export function wallClock(instant: number, timeZone: string): WallClock {
	const parts: Record<string, number> = {};
	for (const p of partsFormatter(timeZone).formatToParts(new Date(instant))) {
		if (p.type !== 'literal') parts[p.type] = Number(p.value);
	}
	return { year: parts.year, month: parts.month, day: parts.day, hour: parts.hour, minute: parts.minute, second: parts.second };
}

/** Offset of `timeZone` from UTC at `instant`, in ms (e.g. +3_600_000 for BST). */
export function offsetAt(instant: number, timeZone: string): number {
	const w = wallClock(instant, timeZone);
	const asUtc = Date.UTC(w.year, w.month - 1, w.day, w.hour, w.minute, w.second);
	return asUtc - Math.floor(instant / 1000) * 1000;
}

/** Minutes since midnight. "24:00" (or "23:59" as an end time) means end of day. */
export function minutesOf(time: string, { asEnd = false } = {}): number {
	if (asEnd && time === '23:59') return 24 * 60;
	const [h, m] = time.split(':').map(Number);
	return h * 60 + m;
}

/** The instant at which the wall clock in `timeZone` reads `date` + `minutes`. */
export function zonedInstant(date: string, minutes: number, timeZone: string): number {
	const [y, mo, d] = date.split('-').map(Number);
	const guess = Date.UTC(y, mo - 1, d) + minutes * MINUTE;
	const first = guess - offsetAt(guess, timeZone);
	// Re-check at the result so DST transitions land on the correct side.
	return guess - offsetAt(first, timeZone);
}

/** Midnight at the start of `date` in `timeZone`. */
export const startOfDate = (date: string, timeZone: string) => zonedInstant(date, 0, timeZone);

/** The calendar date of `instant` in `timeZone`. */
export function dateOf(instant: number, timeZone: string): string {
	const w = wallClock(instant, timeZone);
	return `${w.year}-${pad(w.month)}-${pad(w.day)}`;
}

export function addDays(date: string, days: number): string {
	const [y, m, d] = date.split('-').map(Number);
	return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

/** 0 = Monday … 6 = Sunday. */
export function weekdayOf(date: string): Weekday {
	const [y, m, d] = date.split('-').map(Number);
	return ((new Date(Date.UTC(y, m - 1, d)).getUTCDay() + 6) % 7) as Weekday;
}

export function daysBetween(from: string, to: string): number {
	return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY);
}

/** Every date from `from` to `to` inclusive. */
export function eachDate(from: string, to: string): string[] {
	const out: string[] = [];
	for (let d = from; d <= to; d = addDays(d, 1)) out.push(d);
	return out;
}

export function inRange(date: string, range: { from: string; to: string }): boolean {
	return date >= range.from && date <= range.to;
}

export const pad = (n: number) => String(n).padStart(2, '0');
