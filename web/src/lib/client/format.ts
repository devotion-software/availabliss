/**
 * Human-readable dates and times, always in the signed-in user's time zone.
 * Components call `useFormat()`; the root layout provides the zone.
 */
import { getContext, setContext } from 'svelte';
import { addDays, dayKeyIn, parseKey, timeIn, todayIn, daysBetween } from './dates';

const LOCALE = 'en-GB';
const formatters = new Map<string, Intl.DateTimeFormat>();

function fmt(timeZone: string, opts: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
	const id = timeZone + JSON.stringify(opts);
	let f = formatters.get(id);
	if (!f) {
		f = new Intl.DateTimeFormat(LOCALE, { timeZone, ...opts });
		formatters.set(id, f);
	}
	return f;
}

/** Date keys are calendar days, so they're formatted in UTC to avoid shifting. */
const keyFmt = (key: string, opts: Intl.DateTimeFormatOptions) => fmt('UTC', opts).format(parseKey(key));

const SHORT: Intl.DateTimeFormatOptions = { weekday: 'short', day: 'numeric', month: 'short' };

/** "Fri 13 Nov" — `en-GB` puts a comma after the weekday, which the designs don't use. */
const tidy = (s: string) => s.replace(',', '');

/** "Fri 13 Nov" from a date key. */
export const formatDayKey = (key: string) => tidy(keyFmt(key, SHORT));

/** "13", "Fri", "November 2026", … from a date key. */
export const dayNumber = (key: string) => String(parseKey(key).getUTCDate());
export const weekdayName = (key: string, style: 'short' | 'long' = 'short') => keyFmt(key, { weekday: style });
export const monthTitle = (key: string) => keyFmt(key, { month: 'long', year: 'numeric' });
export const monthShort = (key: string) => keyFmt(key, { month: 'short' });

/** "Fri 13 – Sun 15 Nov", "Mon 30 Nov – Wed 2 Dec", "Fri 13 Nov". */
export function formatDayRange(from: string, to: string, withWeekday = true): string {
	const opts: Intl.DateTimeFormatOptions = withWeekday ? SHORT : { day: 'numeric', month: 'short' };
	if (from === to) return tidy(keyFmt(from, opts));
	const sameYear = from.slice(0, 4) === to.slice(0, 4);
	const sameMonth = sameYear && from.slice(0, 7) === to.slice(0, 7);
	const yearOpts: Intl.DateTimeFormatOptions = sameYear ? {} : { year: 'numeric' };
	const left = sameMonth
		? tidy(keyFmt(from, withWeekday ? { weekday: 'short', day: 'numeric' } : { day: 'numeric' }))
		: tidy(keyFmt(from, { ...opts, ...yearOpts }));
	return `${left} – ${tidy(keyFmt(to, { ...opts, ...yearOpts }))}`;
}

/** "Nov – Jan" for a search window. */
export function formatMonthSpan(from: string, to: string): string {
	const a = monthShort(from);
	const b = monthShort(to);
	return a === b ? a : `${a} – ${b}`;
}

export interface Formatter {
	readonly timeZone: string;
	/** Today's date key in the user's zone. */
	today(): string;
	/** Date key an instant falls on. */
	dayKey(iso: string): string;
	/** "18:30" */
	time(iso: string): string;
	/** "Wed 11 Nov" */
	day(iso: string): string;
	/** "Sunday 1 November" */
	dayLong(iso: string | Date): string;
	/** "Wed 11 Nov · 18:30" */
	dateTime(iso: string): string;
	/** "Wed 11 Nov · 18:30 – 21:00", "Fri 13 – Sun 15 Nov", "Sat 21 Nov" */
	when(start: string, end: string, allDay?: boolean): string;
	/** Start-time hint under a multi-day range: "From Fri 18:00" (empty for midnight starts). */
	startsAt(start: string): string;
	/** "now", "5m ago", "2h ago", "Yesterday", "Mon", "3 Nov" */
	relative(iso: string): string;
	/** Day divider in chat: "Today", "Yesterday", "Mon 2 Nov" */
	dayDivider(iso: string): string;
}

export function createFormatter(timeZone: string): Formatter {
	const dayKey = (iso: string) => dayKeyIn(iso, timeZone);
	const time = (iso: string) => timeIn(iso, timeZone);
	const day = (iso: string) => formatDayKey(dayKey(iso));

	/** Last calendar day an interval touches (an end at exactly midnight belongs to the day before). */
	const lastDay = (end: string) => dayKey(new Date(new Date(end).getTime() - 1).toISOString());

	return {
		timeZone,
		today: () => todayIn(timeZone),
		dayKey,
		time,
		day,
		dayLong: (iso) => tidy(fmt(timeZone, { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date(iso))),
		dateTime: (iso) => `${day(iso)} · ${time(iso)}`,
		when(start, end, allDay = false) {
			const first = dayKey(start);
			const last = lastDay(end);
			if (allDay || first !== last) return formatDayRange(first, last < first ? first : last);
			const endTime = time(end);
			return `${formatDayKey(first)} · ${time(start)} – ${endTime === '00:00' ? 'midnight' : endTime}`;
		},
		startsAt(start) {
			const t = time(start);
			return t === '00:00' ? '' : `From ${weekdayName(dayKey(start))} ${t}`;
		},
		relative(iso) {
			const diff = (Date.now() - new Date(iso).getTime()) / 1000;
			if (diff < 45) return 'now';
			if (diff < 3600) return `${Math.round(diff / 60)}m ago`;
			const days = daysBetween(dayKey(iso), todayIn(timeZone));
			if (days === 0) return `${Math.round(diff / 3600)}h ago`;
			if (days === 1) return 'Yesterday';
			if (days < 7) return weekdayName(dayKey(iso));
			return tidy(keyFmt(dayKey(iso), { day: 'numeric', month: 'short' }));
		},
		dayDivider(iso) {
			const key = dayKey(iso);
			const today = todayIn(timeZone);
			if (key === today) return 'Today';
			if (key === addDays(today, -1)) return 'Yesterday';
			return formatDayKey(key);
		}
	};
}

const KEY = Symbol('format');

/** Called once in the root layout. `getZone` is read lazily so zone changes apply. */
export function provideFormat(getZone: () => string): void {
	let cached: Formatter | null = null;
	setContext(KEY, {
		get current() {
			const zone = getZone();
			if (!cached || cached.timeZone !== zone) cached = createFormatter(zone);
			return cached;
		}
	});
}

/** The formatter for the signed-in user's zone. Read `.current` in markup so it stays reactive. */
export function useFormat(): { readonly current: Formatter } {
	const ctx = getContext<{ readonly current: Formatter } | undefined>(KEY);
	if (!ctx) throw new Error('useFormat() needs provideFormat() in a parent layout');
	return ctx;
}

/** "Sam" from "Sam Taylor"; "S" monogram helpers for avatars. */
export const firstName = (name: string) => name.trim().split(/\s+/)[0] || name;
export function initials(name: string): string {
	const parts = name.trim().split(/\s+/).filter(Boolean);
	if (!parts.length) return '?';
	return parts.length === 1 ? parts[0][0].toUpperCase() : (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/** "Leo and Jade", "Ana, Priya and Tom", "Ana, Priya, Tom and 3 others" */
export function listNames(names: string[], max = 3): string {
	if (names.length <= 1) return names[0] ?? '';
	if (names.length <= max) return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
	const rest = names.length - max;
	return `${names.slice(0, max).join(', ')} and ${rest} other${rest === 1 ? '' : 's'}`;
}

export const plural = (n: number, one: string, many = `${one}s`) => `${n} ${n === 1 ? one : many}`;
