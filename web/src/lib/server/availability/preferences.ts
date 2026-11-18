/**
 * Preferred times: which members' preferences each slot and day fits. Applied after the
 * engine has found what works; preferences never change what works.
 */
import type { Availability, GroupPreference, Weekday } from '#lib/types';
import { addDays, minutesOf, weekdayOf, zonedInstant } from './time';
import { dayLabel } from './labels';

type Pref = Pick<GroupPreference, 'userId' | 'kind' | 'from' | 'to' | 'days' | 'window'>;

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const;
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'] as const;

/** The dates (in `tz`) a span touches. */
function datesOf(start: number, end: number, tz: string): string[] {
	const fmt = new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' });
	const first = fmt.format(new Date(start));
	const last = fmt.format(new Date(Math.max(start, end - 1)));
	const out: string[] = [];
	for (let d = first; d <= last; d = addDays(d, 1)) out.push(d);
	return out;
}

/** Does the span [start, end) fit the preference? Checked on each date it touches. */
export function fits(pref: Pref, start: number, end: number, tz: string): boolean {
	return datesOf(start, end, tz).some((date) => {
		if (pref.kind === 'dates') return !!pref.from && !!pref.to && date >= pref.from && date <= pref.to;
		if (pref.days.length && !pref.days.includes(weekdayOf(date))) return false;
		if (!pref.window) return true;
		const from = zonedInstant(date, minutesOf(pref.window.from), tz);
		const to = zonedInstant(date, minutesOf(pref.window.to, { asEnd: true }), tz);
		return start < to && end > from;
	});
}

/** Adds `preferredBy` to every slot, and to each `match` day (from the slots covering it). */
export function applyPreferences(availability: Availability, prefs: readonly Pref[], tz: string): Availability {
	if (!prefs.length) return availability;
	const slots = availability.slots.map((slot) => {
		const start = Date.parse(slot.start),
			end = Date.parse(slot.end);
		const who = [...new Set(prefs.filter((p) => fits(p, start, end, tz)).map((p) => p.userId))];
		return { ...slot, preferredBy: who };
	});
	const days = availability.days.map((day) => {
		if (day.status !== 'match') return day;
		const dayStart = zonedInstant(day.date, 0, tz),
			dayEnd = zonedInstant(addDays(day.date, 1), 0, tz);
		const who = new Set<string>();
		for (const slot of slots) {
			const start = Math.max(Date.parse(slot.start), dayStart),
				end = Math.min(Date.parse(slot.end), dayEnd);
			if (end <= start) continue;
			for (const p of prefs) if (fits(p, start, end, tz)) who.add(p.userId);
		}
		return { ...day, preferredBy: [...who] };
	});
	return { ...availability, slots, days };
}

function daysPart(days: readonly Weekday[]): string {
	const sorted = [...days].sort();
	const key = sorted.join(',');
	if (!sorted.length || sorted.length === 7) return 'Any day';
	if (key === '5,6') return 'Weekends';
	if (key === '0,1,2,3,4') return 'Weekdays';
	const contiguous = sorted.every((d, i) => i === 0 || d === sorted[i - 1] + 1);
	if (contiguous && sorted.length > 2) return `${WEEKDAYS[sorted[0]]}–${WEEKDAYS[sorted[sorted.length - 1]]}`;
	return sorted.map((d) => WEEKDAYS[d]).join(', ');
}

/** "Weekends after 16:00", "Weekdays 12:00–14:00", "12 – 14 Dec", "Sat 20 Dec". */
export function preferenceLabel(pref: Pref, tz: string): string {
	if (pref.kind === 'dates' && pref.from && pref.to) {
		const day = (d: string) => `${Number(d.slice(8, 10))} ${MONTHS[Number(d.slice(5, 7)) - 1]}`;
		if (pref.from === pref.to) return dayLabel(zonedInstant(pref.from, 12 * 60, tz), tz);
		return pref.from.slice(5, 7) === pref.to.slice(5, 7)
			? `${Number(pref.from.slice(8, 10))} – ${day(pref.to)}`
			: `${day(pref.from)} – ${day(pref.to)}`;
	}
	const days = daysPart(pref.days);
	if (!pref.window) return days;
	const endsLate = minutesOf(pref.window.to, { asEnd: true }) >= 24 * 60;
	const startsEarly = minutesOf(pref.window.from) === 0;
	if (endsLate && !startsEarly) return `${days} after ${pref.window.from}`;
	if (startsEarly && !endsLate) return `${days} before ${pref.window.to}`;
	return `${days} ${pref.window.from}–${pref.window.to}`;
}
