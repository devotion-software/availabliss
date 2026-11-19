/**
 * Group timing ("requirements"): the UI presets, mapped exactly as the table in
 * docs/API.md, and the one-line summaries shown around the app.
 */
import type { DateRange, Requirement, RequirementLength, TimeWindow, Weekday } from '#lib/types';
import { addDays, addMonths } from './dates';
import { formatDayRange, formatMonthSpan, plural } from './format';
import { api } from './api';

export const WEEKDAYS: readonly Weekday[] = [0, 1, 2, 3, 4, 5, 6];
export const WEEKDAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'] as const;
export const weekdayShort = (d: number) => WEEKDAY_NAMES[d].slice(0, 3);

export type PresetId = '2h' | '4h' | 'evening' | 'day' | 'weekend' | 'tue-thu' | 'tue-next-thu' | 'week' | 'custom';

interface Preset {
	id: PresetId;
	label: string;
	length?: RequirementLength;
	window?: TimeWindow | null;
}

const ACTIVE_HOURS: TimeWindow = { from: '08:00', to: '23:00' };

export const PRESETS: readonly Preset[] = [
	{ id: '2h', label: '2 hours', length: { type: 'duration', minutes: 120 }, window: ACTIVE_HOURS },
	{ id: '4h', label: '4 hours', length: { type: 'duration', minutes: 240 }, window: ACTIVE_HOURS },
	{ id: 'evening', label: 'An evening', length: { type: 'days', count: 1 }, window: { from: '18:00', to: '23:59' } },
	{ id: 'day', label: 'One day', length: { type: 'days', count: 1 }, window: null },
	{
		id: 'weekend',
		label: 'A weekend',
		length: { type: 'span', startDay: 4, startTime: '18:00', endDay: 6, endTime: '23:59', weeksLater: 0 },
		window: null
	},
	{
		id: 'tue-thu',
		label: 'Tue → Thu',
		length: { type: 'span', startDay: 1, startTime: '00:00', endDay: 3, endTime: '23:59', weeksLater: 0 },
		window: null
	},
	{
		id: 'tue-next-thu',
		label: 'Tue → following Thu',
		length: { type: 'span', startDay: 1, startTime: '00:00', endDay: 3, endTime: '23:59', weeksLater: 1 },
		window: null
	},
	{ id: 'week', label: 'A week', length: { type: 'days', count: 7 }, window: null },
	{ id: 'custom', label: 'Custom' }
];

const sameJson = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);

/** Which preset a requirement corresponds to ("custom" when none match exactly). */
export function presetOf(req: Requirement): PresetId {
	const { length, window } = req;
	if (length.type === 'duration') {
		if (length.minutes === 120) return '2h';
		if (length.minutes === 240) return '4h';
		return 'custom';
	}
	// One day that starts at 16:00 or later is an evening, whatever time it starts or ends.
	if (isEvening(req)) return 'evening';
	const match = PRESETS.find((p) => {
		if (!p.length || !sameJson(p.length, length)) return false;
		// Duration presets ignore the window; day-shaped ones are defined by it.
		return p.length.type === 'span' || sameJson(p.window ?? null, window);
	});
	return match?.id ?? 'custom';
}

/** The hour from which a one-day window counts as an evening. */
export const EVENING_FROM = '16:00';

/** One day with a window starting at 16:00 or later ("An evening from 19:00"). */
export function isEvening(req: Pick<Requirement, 'length' | 'window'>): boolean {
	return req.length.type === 'days' && req.length.count === 1 && req.window !== null && req.window.from >= EVENING_FROM;
}

/** The requirement with a preset applied. "custom" leaves it as it is. */
export function applyPreset(req: Requirement, id: PresetId): Requirement {
	const preset = PRESETS.find((p) => p.id === id);
	if (!preset?.length) return req;
	const window = preset.length.type === 'duration' && req.length.type === 'duration' && req.window ? req.window : (preset.window ?? null);
	// "A week" (7 days in a row) can never fit in Fri–Sun: open up every day when the run is longer than the days allowed.
	const days = preset.length.type === 'days' && preset.length.count > req.days.length ? ALL_DAYS : req.days;
	return { ...req, length: structuredClone(preset.length), window, days: [...days] };
}

const ALL_DAYS: Weekday[] = [0, 1, 2, 3, 4, 5, 6];

/** A fresh requirement: a weekend within the next three months. */
export function defaultRequirement(timeZone: string, today: string, preset: PresetId = 'weekend'): Requirement {
	const base: Requirement = {
		length: { type: 'days', count: 1 },
		window: null,
		days: [4, 5, 6],
		bankHolidays: true,
		schoolHolidaysOnly: false,
		schoolAuthority: null,
		customSchoolHolidays: [],
		range: { from: today, to: addDays(addMonths(today, 3), -1) },
		dates: [],
		timezone: timeZone
	};
	return applyPreset(base, preset);
}

/** "Fri–Sun", "weekdays", "weekends", "any day", "Mon, Wed, Fri" */
export function summarizeDays(days: readonly number[]): string {
	const sorted = [...new Set(days)].sort((a, b) => a - b);
	// An empty list means any weekday (docs/API.md).
	if (sorted.length === 0 || sorted.length === 7) return 'any day';
	if (sameJson(sorted, [0, 1, 2, 3, 4])) return 'weekdays';
	if (sameJson(sorted, [5, 6])) return 'weekends';
	const contiguous = sorted.every((d, i) => i === 0 || d === sorted[i - 1] + 1);
	if (contiguous && sorted.length > 2) return `${weekdayShort(sorted[0])}–${weekdayShort(sorted[sorted.length - 1])}`;
	return sorted.map(weekdayShort).join(', ');
}

/** Short name for the shape: "A weekend", "2 hours", "An evening from 18:00". */
export function lengthLabel(req: Requirement): string {
	const preset = presetOf(req);
	const { length } = req;
	if (preset === 'evening') return `An evening from ${req.window?.from ?? '18:00'}`;
	if (preset !== 'custom') return PRESETS.find((p) => p.id === preset)!.label;
	if (length.type === 'duration') {
		const h = length.minutes / 60;
		return Number.isInteger(h) ? plural(h, 'hour') : plural(length.minutes, 'minute');
	}
	if (length.type === 'days') return length.count === 7 ? 'A week' : length.count === 1 ? 'One day' : `${length.count} days`;
	return 'A span';
}

/** "Fri from 18:00 → Sun", "Tue → following Thu", "Tue → Thu 3 weeks later" */
export function spanLabel(length: Extract<RequirementLength, { type: 'span' }>): string {
	const start = `${weekdayShort(length.startDay)}${length.startTime === '00:00' ? '' : ` from ${length.startTime}`}`;
	const endDay = `${length.weeksLater === 1 ? 'following ' : ''}${weekdayShort(length.endDay)}`;
	const end = length.endTime === '23:59' ? endDay : `${endDay} ${length.endTime}`;
	return `${start} → ${end}${length.weeksLater >= 2 ? ` ${length.weeksLater} weeks later` : ''}`;
}

/** Everything after the shape: "Fri from 18:00 → Sun · Nov – Jan". */
export function detailLabel(req: Requirement): string {
	const bits: string[] = [];
	const { length } = req;
	if (length.type === 'span') bits.push(spanLabel(length));
	else {
		if (length.type === 'duration' && req.window) bits.push(`${req.window.from}–${req.window.to}`);
		if (length.type === 'days' && req.window && presetOf(req) !== 'evening') bits.push(`${req.window.from}–${req.window.to}`);
		bits.push(summarizeDays(req.days));
	}
	if (req.schoolHolidaysOnly) bits.push('school holidays only');
	bits.push(req.dates.length ? plural(req.dates.length, 'date range') : formatMonthSpan(req.range.from, req.range.to));
	return bits.join(' · ');
}

/** The full one-liner: "A weekend · Fri from 18:00 → Sun · Nov – Jan". */
export const summarize = (req: Requirement) => `${lengthLabel(req)} · ${detailLabel(req)}`;

export interface RequirementPreview {
	count: number;
	from: string;
	to: string;
	summary: string;
}

/** How many candidate slots a requirement produces, before anyone's busy time. */
export const previewRequirement = (requirement: Requirement) => api.post<RequirementPreview>('/requirements/preview', { requirement });

/** What one slot of a requirement is called, as the server's `slotNoun`, with "time" for durations. */
export function slotNounOf(req: Requirement): string {
	const l = req.length;
	if (l.type === 'duration') return 'time';
	if (l.type === 'span') return l.startDay === 4 && l.endDay === 6 && l.weeksLater === 0 ? 'weekend' : 'date';
	if (l.count === 7) return 'week';
	if (l.count === 1) return isEvening(req) ? 'evening' : 'day';
	return 'date';
}

/** "13 weekends", "40 evenings", "14 dates", "1 day". */
export function fitCountLabel(req: Requirement, count: number): string {
	return plural(count, slotNounOf(req));
}

/** "Propose this weekend"; "Propose these dates" for a slot over several days ("this date" for one). */
export function proposeLabel(noun: string, multiDay: boolean): string {
	if (noun === 'date') return multiDay ? 'Propose these dates' : 'Propose this date';
	return `Propose this ${noun}`;
}

/** The range in `ranges` that already holds every day of `range`, if any (an identical one included). */
export const coveringRange = (range: DateRange, ranges: readonly DateRange[]) => ranges.find((r) => r.from <= range.from && range.to <= r.to);

/** The ranges that `range` overlaps or runs straight on from, so would be joined with it. */
export const joiningRanges = (range: DateRange, ranges: readonly DateRange[]) =>
	ranges.filter((r) => addDays(r.to, 1) >= range.from && addDays(range.to, 1) >= r.from);

/** Sorted, with ranges that overlap or touch (one ends the day before the next starts) joined into one. */
export function mergeRanges(ranges: readonly DateRange[]): DateRange[] {
	const merged: DateRange[] = [];
	for (const range of [...ranges].sort((a, b) => a.from.localeCompare(b.from))) {
		const last = merged.at(-1);
		if (last && range.from <= addDays(last.to, 1)) last.to = range.to > last.to ? range.to : last.to;
		else merged.push({ ...range });
	}
	return merged;
}

/** What to tell someone adding `range`: why it can't be added (`blocked`), or what it'll be joined with. */
export function rangeNote(range: DateRange, ranges: readonly DateRange[]): { text: string; blocked: boolean } | null {
	const label = (r: DateRange) => formatDayRange(r.from, r.to, false);
	const covering = coveringRange(range, ranges);
	if (covering) {
		const same = covering.from === range.from && covering.to === range.to;
		return { text: same ? 'You\u2019ve already added these dates.' : `Already included in ${label(covering)}.`, blocked: true };
	}
	const joining = joiningRanges(range, ranges);
	if (joining.length === 0) return null;
	const text =
		joining.length === 1
			? `Joins up with ${label(joining[0])}, so they\u2019ll become one range.`
			: `Joins up with ${joining.length} of your ranges, so they\u2019ll become one.`;
	return { text, blocked: false };
}

// ─── The timing sentence ("We're looking for a weekend, from Friday 18:00 to Sunday night…") ───

/** Each preset as it reads in the sentence. */
const KIND_PHRASES: Record<Exclude<PresetId, 'custom'>, string> = {
	'2h': '2 hours',
	'4h': '4 hours',
	evening: 'an evening',
	day: 'a whole day',
	weekend: 'a weekend',
	'tue-thu': 'Tuesday to Thursday',
	'tue-next-thu': 'Tuesday to the following Thursday',
	week: 'a week'
};

/** The first gap: "a weekend", "2 hours", "3 days", "2½ hours". */
export function kindPhrase(req: Requirement): string {
	const preset = presetOf(req);
	if (preset !== 'custom') return KIND_PHRASES[preset];
	const { length } = req;
	if (length.type === 'duration') {
		const h = length.minutes / 60;
		if (Number.isInteger(h)) return plural(h, 'hour');
		if (Number.isInteger(h * 2)) return `${Math.floor(h) || ''}½ hours`.trim();
		return plural(length.minutes, 'minute');
	}
	if (length.type === 'days') return length.count === 1 ? 'a day' : `${length.count} days in a row`;
	return 'a stretch of days';
}

/** "Friday 18:00", "Tuesday" (from the start of the day). */
export const spanStartPhrase = (length: Extract<RequirementLength, { type: 'span' }>) =>
	`${WEEKDAY_NAMES[length.startDay]}${length.startTime === '00:00' ? '' : ` ${length.startTime}`}`;

/** "Sunday night" (the end of the day), "Sunday 17:00", "the following Thursday night", "Thursday night 3 weeks later". */
export function spanEndPhrase(length: Extract<RequirementLength, { type: 'span' }>): string {
	const day = `${length.weeksLater === 1 ? 'the following ' : ''}${WEEKDAY_NAMES[length.endDay]}`;
	const end = length.endTime === '23:59' ? `${day} night` : `${day} ${length.endTime}`;
	return length.weeksLater >= 2 ? `${end} ${length.weeksLater} weeks later` : end;
}

/** The choices for when a span ends: "The same week", "The week after", "2 weeks later" … */
export const WEEKS_LATER_CHOICES = [0, 1, 2, 3, 4].map((weeks) => ({
	weeks,
	label: weeks === 0 ? 'Same week' : weeks === 1 ? 'Week after' : `${weeks} weeks later`
}));

/** "any time of day", "between 18:00 and midnight", "between 08:00 and 23:00". */
export function windowPhrase(window: TimeWindow | null): string {
	if (!window) return 'any time of day';
	return `between ${window.from} and ${window.to === '23:59' ? 'midnight' : window.to}`;
}

/** "any day", "Fridays to Sundays", "weekdays", "Mondays, Wednesdays and Fridays". */
export function daysPhrase(days: readonly number[]): string {
	const sorted = [...new Set(days)].sort((a, b) => a - b);
	if (sorted.length === 0 || sorted.length === 7) return 'any day';
	if (sameJson(sorted, [0, 1, 2, 3, 4])) return 'weekdays';
	if (sameJson(sorted, [5, 6])) return 'weekends';
	const names = sorted.map((d) => `${WEEKDAY_NAMES[d]}s`);
	const contiguous = sorted.every((d, i) => i === 0 || d === sorted[i - 1] + 1);
	if (contiguous && sorted.length > 2) return `${names[0]} to ${names.at(-1)}`;
	return names.length === 1 ? names[0] : `${names.slice(0, -1).join(', ')} and ${names.at(-1)}`;
}
