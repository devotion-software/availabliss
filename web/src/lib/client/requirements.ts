/**
 * Group timing ("requirements"): the UI presets, mapped exactly as the table in
 * docs/API.md, and the one-line summaries shown around the app.
 */
import type { Requirement, RequirementLength, TimeWindow, Weekday } from '#lib/types';
import { addDays, addMonths } from './dates';
import { formatMonthSpan, plural } from './format';
import { api } from './api';

export const WEEKDAYS: readonly Weekday[] = [0, 1, 2, 3, 4, 5, 6];
export const WEEKDAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'] as const;
export const weekdayShort = (d: number) => WEEKDAY_NAMES[d].slice(0, 3);

export type PresetId = '2h' | '4h' | 'evening' | 'day' | 'weekend' | 'tue-thu' | 'tue-next-thu' | 'week' | 'custom';

interface Preset {
	id: PresetId;
	label: string;
	/** Noun for "2 weekends work". */
	noun: string;
	length?: RequirementLength;
	window?: TimeWindow | null;
}

const ACTIVE_HOURS: TimeWindow = { from: '08:00', to: '23:00' };

export const PRESETS: readonly Preset[] = [
	{ id: '2h', label: '2 hours', noun: 'time', length: { type: 'duration', minutes: 120 }, window: ACTIVE_HOURS },
	{ id: '4h', label: '4 hours', noun: 'time', length: { type: 'duration', minutes: 240 }, window: ACTIVE_HOURS },
	{ id: 'evening', label: 'An evening', noun: 'evening', length: { type: 'days', count: 1 }, window: { from: '18:00', to: '23:59' } },
	{ id: 'day', label: 'One day', noun: 'day', length: { type: 'days', count: 1 }, window: null },
	{
		id: 'weekend',
		label: 'A weekend',
		noun: 'weekend',
		length: { type: 'span', startDay: 4, startTime: '18:00', endDay: 6, endTime: '23:59', weeksLater: 0 },
		window: null
	},
	{
		id: 'tue-thu',
		label: 'Tue → Thu',
		noun: 'option',
		length: { type: 'span', startDay: 1, startTime: '00:00', endDay: 3, endTime: '23:59', weeksLater: 0 },
		window: null
	},
	{
		id: 'tue-next-thu',
		label: 'Tue → following Thu',
		noun: 'option',
		length: { type: 'span', startDay: 1, startTime: '00:00', endDay: 3, endTime: '23:59', weeksLater: 1 },
		window: null
	},
	{ id: 'week', label: 'A week', noun: 'week', length: { type: 'days', count: 7 }, window: null },
	{ id: 'custom', label: 'Custom', noun: 'option' }
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
	const match = PRESETS.find((p) => {
		if (!p.length || !sameJson(p.length, length)) return false;
		// Duration presets ignore the window; day-shaped ones are defined by it.
		return p.length.type === 'span' || sameJson(p.window ?? null, window);
	});
	return match?.id ?? 'custom';
}

/** The requirement with a preset applied. "custom" leaves it as it is. */
export function applyPreset(req: Requirement, id: PresetId): Requirement {
	const preset = PRESETS.find((p) => p.id === id);
	if (!preset?.length) return req;
	const window =
		preset.length.type === 'duration' && req.length.type === 'duration' && req.window ? req.window : (preset.window ?? null);
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

/** "Fri from 18:00 → Sun", "Tue → following Thu" */
export function spanLabel(length: Extract<RequirementLength, { type: 'span' }>): string {
	const start = `${weekdayShort(length.startDay)}${length.startTime === '00:00' ? '' : ` from ${length.startTime}`}`;
	const endDay = `${length.weeksLater ? 'following ' : ''}${weekdayShort(length.endDay)}`;
	const end = length.endTime === '23:59' ? endDay : `${endDay} ${length.endTime}`;
	return `${start} → ${end}`;
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

/** "2 weekends work", "1 evening works", "No times work yet". */
export function matchesLabel(req: Requirement | null, count: number): string {
	if (count === 0) return 'No times work yet';
	const noun = req ? (PRESETS.find((p) => p.id === presetOf(req))?.noun ?? 'option') : 'time';
	return `${plural(count, noun)} work${count === 1 ? 's' : ''}`;
}

export interface RequirementPreview {
	count: number;
	from: string;
	to: string;
	summary: string;
}

/** How many candidate slots a requirement produces, before anyone's busy time. */
export const previewRequirement = (requirement: Requirement) =>
	api.post<RequirementPreview>('/requirements/preview', { requirement });

/** "13 weekends", "40 evenings", "1 day". */
export function fitCountLabel(req: Requirement, count: number): string {
	const noun = PRESETS.find((p) => p.id === presetOf(req))?.noun ?? 'option';
	return plural(count, noun);
}
