/** Human-readable labels for slots and requirements (British English, the group's timezone). */
import type { Requirement, SlotNoun, Weekday } from '#lib/types';
import type { Span } from './intervals';
import { dateOf, minutesOf, wallClock } from './time';

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const;
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'] as const;

const dayFormatters = new Map<string, Intl.DateTimeFormat>();
function dayFormatter(timeZone: string) {
	let f = dayFormatters.get(timeZone);
	if (!f) {
		f = new Intl.DateTimeFormat('en-GB', { timeZone, weekday: 'short', day: 'numeric', month: 'short' });
		dayFormatters.set(timeZone, f);
	}
	return f;
}

/** "Fri 13 Nov" */
export function dayLabel(instant: number, timeZone: string): string {
	return dayFormatter(timeZone).format(new Date(instant)).replace(',', '');
}

/** "18:00" */
export function timeLabel(instant: number, timeZone: string): string {
	const w = wallClock(instant, timeZone);
	return `${String(w.hour).padStart(2, '0')}:${String(w.minute).padStart(2, '0')}`;
}

/** "Fri 13 – Sun 15 Nov", "Thu 5 Nov · 18:00–21:00", "Sat 7 Nov". */
export function slotLabel(span: Span, req: Requirement): string {
	const tz = req.timezone;
	// The end is exclusive, so a slot ending at midnight belongs to the previous day.
	const lastInstant = span.end - 1;
	const sameDay = dateOf(span.start, tz) === dateOf(lastInstant, tz);

	if (!sameDay) {
		const [startDay, startNum, startMonth] = dayLabel(span.start, tz).split(' ');
		const end = dayLabel(lastInstant, tz);
		return startMonth === end.split(' ')[2] ? `${startDay} ${startNum} – ${end}` : `${startDay} ${startNum} ${startMonth} – ${end}`;
	}

	const day = dayLabel(span.start, tz);
	const startsAtMidnight = timeLabel(span.start, tz) === '00:00';
	const endsAtMidnight = timeLabel(span.end, tz) === '00:00';
	if (startsAtMidnight && endsAtMidnight) return day;
	if (endsAtMidnight) return `${day} · from ${timeLabel(span.start, tz)}`;
	return `${day} · ${timeLabel(span.start, tz)}–${timeLabel(span.end, tz)}`;
}

function daysLabel(days: readonly Weekday[]): string {
	if (days.length === 0 || days.length === 7) return 'any day';
	const sorted = [...days].sort();
	const contiguous = sorted.every((d, i) => i === 0 || d === sorted[i - 1] + 1);
	if (contiguous && sorted.length > 2) return `${WEEKDAYS[sorted[0]]}–${WEEKDAYS[sorted[sorted.length - 1]]}`;
	return sorted.map((d) => WEEKDAYS[d]).join(', ');
}

function monthSpan(from: string, to: string): string {
	const m = (date: string) => MONTHS[Number(date.slice(5, 7)) - 1];
	return m(from) === m(to) && from.slice(0, 4) === to.slice(0, 4) ? m(from) : `${m(from)} – ${m(to)}`;
}

/** The one-line summary shown on group cards: "A weekend · Fri 18:00 → Sun · Nov – Jan". */
export function requirementSummary(req: Requirement): string {
	const l = req.length;
	let what: string;
	if (l.type === 'span') {
		const isWeekend = l.startDay === 4 && l.endDay === 6 && l.weeksLater === 0;
		const start = l.startTime === '00:00' ? WEEKDAYS[l.startDay] : `${WEEKDAYS[l.startDay]} ${l.startTime}`;
		const end = minutesOf(l.endTime, { asEnd: true }) === 24 * 60 ? WEEKDAYS[l.endDay] : `${WEEKDAYS[l.endDay]} ${l.endTime}`;
		what = `${isWeekend ? 'A weekend' : l.weeksLater ? 'Over a week' : 'A few days'} · ${start} → ${l.weeksLater ? 'following ' : ''}${end}`;
	} else if (l.type === 'days') {
		const label = l.count === 7 ? 'A week' : l.count === 1 ? (req.window ? 'An evening' : 'One day') : `${l.count} days`;
		const window = req.window ? ` from ${req.window.from}` : '';
		what = `${label}${l.count === 1 && req.window ? window : ''} · ${daysLabel(req.days)}`;
	} else {
		const hours = l.minutes / 60;
		what = `${Number.isInteger(hours) ? hours : hours.toFixed(1)} hour${hours === 1 ? '' : 's'} · ${daysLabel(req.days)}`;
	}
	const extras = [req.schoolHolidaysOnly && 'school holidays', req.dates.length > 0 && 'chosen dates'].filter(Boolean);
	return [what, ...extras, monthSpan(req.range.from, req.range.to)].join(' · ');
}

/** The word for one slot of this requirement. */
export function slotNoun(req: Requirement): SlotNoun {
	const l = req.length;
	if (l.type === 'span') return l.startDay === 4 && l.endDay === 6 && l.weeksLater === 0 ? 'weekend' : 'trip';
	if (l.type === 'days') return l.count === 7 ? 'week' : l.count === 1 ? (req.window ? 'evening' : 'day') : 'trip';
	return 'slot';
}
