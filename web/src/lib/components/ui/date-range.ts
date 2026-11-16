/**
 * The rules behind the shared date range input (start date + time → end date + time):
 * moving the start keeps the duration, an end before the start snaps back after it.
 * Wall-clock only: dates are `YYYY-MM-DD`, times `HH:MM`, converted to instants elsewhere.
 */
import { addDays, daysBetween, parseKey } from '#lib/client/dates';

export interface RangeFields {
	startDate: string;
	startTime: string;
	endDate: string;
	endTime: string;
	allDay: boolean;
}

const DAY = 24 * 60;
/** What an end snaps to when there's no sensible previous duration. */
export const DEFAULT_DURATION = 60;
/** The shortest duration kept when an end is picked before the start. */
export const MIN_DURATION = 15;

const pad = (n: number) => String(n).padStart(2, '0');
const hm = (time: string) => {
	const [h, m] = time.split(':').map(Number);
	return h * 60 + m;
};

/** Minutes since 1970-01-01 00:00 on the wall clock. */
export const wallMinutes = (date: string, time: string) => (parseKey(date).getTime() / 60_000) + hm(time);

/** The date and time `minutes` after `date` `time`. */
export function shiftWall(date: string, time: string, minutes: number): { date: string; time: string } {
	const total = hm(time) + minutes;
	const days = Math.floor(total / DAY);
	const rest = total - days * DAY;
	return { date: addDays(date, days), time: `${pad(Math.floor(rest / 60))}:${pad(rest % 60)}` };
}

const complete = (r: RangeFields) => !!(r.startDate && r.endDate && (r.allDay || (r.startTime && r.endTime)));

/** Timed: minutes from start to end. All day: whole days from the first to the last. */
export function duration(r: RangeFields): number {
	return r.allDay ? daysBetween(r.startDate, r.endDate) : wallMinutes(r.endDate, r.endTime) - wallMinutes(r.startDate, r.startTime);
}

/** A new start (date and/or time). The end moves by the same amount, so the duration stays. */
export function withStart(r: RangeFields, next: { date?: string; time?: string }): RangeFields {
	const startDate = next.date ?? r.startDate;
	const startTime = next.time ?? r.startTime;
	if (!complete(r)) return { ...r, startDate, startTime, endDate: r.endDate && r.endDate < startDate ? startDate : r.endDate };
	if (r.allDay) {
		const days = Math.max(0, duration(r));
		return { ...r, startDate, startTime, endDate: addDays(startDate, days) };
	}
	const minutes = duration(r) > 0 ? duration(r) : DEFAULT_DURATION;
	const end = shiftWall(startDate, startTime, minutes);
	return { ...r, startDate, startTime, endDate: end.date, endTime: end.time };
}

/** A new end (date and/or time). One that lands at or before the start snaps to after it. */
export function withEnd(r: RangeFields, next: { date?: string; time?: string }): RangeFields {
	const out = { ...r, endDate: next.date ?? r.endDate, endTime: next.time ?? r.endTime };
	if (!complete(out)) return out;
	if (r.allDay) return out.endDate < out.startDate ? { ...out, endDate: out.startDate } : out;
	if (duration(out) > 0) return out;
	const old = complete(r) ? duration(r) : 0;
	const end = shiftWall(r.startDate, r.startTime, old >= MIN_DURATION ? old : DEFAULT_DURATION);
	return { ...out, endDate: end.date, endTime: end.time };
}

/** Switching all day on or off; a timed range that would end before it starts gets the default length. */
export function withAllDay(r: RangeFields, allDay: boolean): RangeFields {
	const out = { ...r, allDay };
	if (!complete(out)) return out;
	if (allDay) return out.endDate < out.startDate ? { ...out, endDate: out.startDate } : out;
	if (duration(out) > 0) return out;
	const end = shiftWall(r.startDate, r.startTime, DEFAULT_DURATION);
	return { ...out, endDate: end.date, endTime: end.time };
}

const longFmt = new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' });
/** "Monday 16 November", for screen readers. */
export const spokenDate = (date: string) => longFmt.format(parseKey(date)).replace(',', '');
