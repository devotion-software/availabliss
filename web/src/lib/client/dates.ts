/**
 * Calendar-day arithmetic on `YYYY-MM-DD` keys, plus conversions between
 * instants (ISO strings) and wall-clock time in an IANA time zone.
 * Weekdays follow the API: 0 = Monday … 6 = Sunday.
 */
import type { Weekday } from '#lib/types';

const DAY_MS = 86_400_000;

const pad = (n: number) => String(n).padStart(2, '0');

/** UTC midnight of a `YYYY-MM-DD` key. */
export function parseKey(key: string): Date {
	const [y, m, d] = key.split('-').map(Number);
	return new Date(Date.UTC(y, m - 1, d));
}

export function toKey(date: Date): string {
	return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

export const addDays = (key: string, days: number) => toKey(new Date(parseKey(key).getTime() + days * DAY_MS));

/** First day of the month `months` away from `key`'s month. */
export function addMonths(key: string, months: number): string {
	const d = parseKey(key);
	return toKey(new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + months, 1)));
}

export const weekdayOf = (key: string) => ((parseKey(key).getUTCDay() + 6) % 7) as Weekday;
export const startOfWeek = (key: string) => addDays(key, -weekdayOf(key));
export const startOfMonth = (key: string) => key.slice(0, 8) + '01';
export const endOfMonth = (key: string) => addDays(addMonths(key, 1), -1);
export const daysBetween = (from: string, to: string) => Math.round((parseKey(to).getTime() - parseKey(from).getTime()) / DAY_MS);

/** Every key from `from` to `to`, inclusive. */
export function eachDay(from: string, to: string): string[] {
	const out: string[] = [];
	for (let k = from; k <= to; k = addDays(k, 1)) out.push(k);
	return out;
}

const partsFormatters = new Map<string, Intl.DateTimeFormat>();
function zonedParts(date: Date, timeZone: string) {
	let f = partsFormatters.get(timeZone);
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
		partsFormatters.set(timeZone, f);
	}
	const p = Object.fromEntries(f.formatToParts(date).map((x) => [x.type, x.value]));
	return { y: +p.year, m: +p.month, d: +p.day, h: +p.hour % 24, mi: +p.minute, s: +p.second };
}

/** Milliseconds the zone is ahead of UTC at `date`. */
function offsetMs(date: Date, timeZone: string): number {
	const p = zonedParts(date, timeZone);
	return Date.UTC(p.y, p.m - 1, p.d, p.h, p.mi, p.s) - Math.floor(date.getTime() / 1000) * 1000;
}

/** The calendar day an instant falls on in `timeZone`. */
export function dayKeyIn(iso: string | Date, timeZone: string): string {
	const p = zonedParts(typeof iso === 'string' ? new Date(iso) : iso, timeZone);
	return `${p.y}-${pad(p.m)}-${pad(p.d)}`;
}

/** Wall-clock `HH:MM` of an instant in `timeZone`. */
export function timeIn(iso: string, timeZone: string): string {
	const p = zonedParts(new Date(iso), timeZone);
	return `${pad(p.h)}:${pad(p.mi)}`;
}

export const todayIn = (timeZone: string) => dayKeyIn(new Date(), timeZone);

/** The instant (ISO, UTC) of a wall-clock date and time in `timeZone`. Handles DST. */
export function zonedToIso(key: string, time: string, timeZone: string): string {
	const [y, m, d] = key.split('-').map(Number);
	const [h, mi] = time.split(':').map(Number);
	const wall = Date.UTC(y, m - 1, d, h, mi);
	let utc = wall - offsetMs(new Date(wall), timeZone);
	// A second pass settles instants near a DST change.
	utc = wall - offsetMs(new Date(utc), timeZone);
	return new Date(utc).toISOString();
}

/** Minutes from the start of `dayKey` (in `timeZone`) to an instant; may be < 0 or > 1440. */
export function minutesIntoDay(iso: string, dayKey: string, timeZone: string): number {
	return (new Date(iso).getTime() - new Date(zonedToIso(dayKey, '00:00', timeZone)).getTime()) / 60_000;
}

export const minutesToTime = (minutes: number) => `${pad(Math.floor(minutes / 60) % 24)}:${pad(minutes % 60)}`;

/** Whether two half-open intervals overlap. */
export const overlaps = (aStart: string, aEnd: string, bStart: string, bEnd: string) =>
	new Date(aStart) < new Date(bEnd) && new Date(bStart) < new Date(aEnd);

/** The browser's IANA zone, for defaults before we know the user's. */
export const browserTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
