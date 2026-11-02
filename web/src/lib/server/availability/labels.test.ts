import { describe, expect, test } from 'bun:test';
import type { Requirement, RequirementLength } from '#lib/types';
import { dayLabel, requirementSummary, slotLabel, slotNoun, timeLabel } from './labels';

const req = (length: RequirementLength, over: Partial<Requirement> = {}): Requirement => ({
	length,
	window: null,
	days: [],
	bankHolidays: false,
	schoolHolidaysOnly: false,
	schoolAuthority: null,
	customSchoolHolidays: [],
	range: { from: '2026-11-01', to: '2027-01-31' },
	dates: [],
	timezone: 'Europe/London',
	...over
});

const WEEKEND: RequirementLength = { type: 'span', startDay: 4, startTime: '18:00', endDay: 6, endTime: '23:59', weeksLater: 0 };
const at = (s: string) => Date.parse(s);

describe('dayLabel / timeLabel', () => {
	test('in the requirement timezone', () => {
		expect(dayLabel(at('2026-11-13T23:30:00Z'), 'Europe/London')).toBe('Fri 13 Nov');
		expect(dayLabel(at('2026-11-13T23:30:00Z'), 'Asia/Tokyo')).toBe('Sat 14 Nov');
		expect(timeLabel(at('2026-07-01T17:05:00Z'), 'Europe/London')).toBe('18:05');
	});
});

describe('slotLabel', () => {
	const r = req(WEEKEND);

	test('multi-day spans in one month and across months', () => {
		expect(slotLabel({ start: at('2026-11-13T18:00:00Z'), end: at('2026-11-16T00:00:00Z') }, r)).toBe('Fri 13 – Sun 15 Nov');
		expect(slotLabel({ start: at('2026-11-27T18:00:00Z'), end: at('2026-12-02T00:00:00Z') }, r)).toBe('Fri 27 Nov – Tue 1 Dec');
	});

	test('whole days, evenings and timed slots', () => {
		expect(slotLabel({ start: at('2026-11-14T00:00:00Z'), end: at('2026-11-15T00:00:00Z') }, r)).toBe('Sat 14 Nov');
		expect(slotLabel({ start: at('2026-11-14T18:00:00Z'), end: at('2026-11-15T00:00:00Z') }, r)).toBe('Sat 14 Nov · from 18:00');
		expect(slotLabel({ start: at('2026-11-14T10:00:00Z'), end: at('2026-11-14T12:30:00Z') }, r)).toBe('Sat 14 Nov · 10:00–12:30');
	});
});

describe('requirementSummary', () => {
	test('spans', () => {
		expect(requirementSummary(req(WEEKEND))).toBe('A weekend · Fri 18:00 → Sun · Nov – Jan');
		expect(
			requirementSummary(req({ type: 'span', startDay: 1, startTime: '00:00', endDay: 3, endTime: '23:59', weeksLater: 1 }))
		).toBe('Over a week · Tue → following Thu · Nov – Jan');
		expect(
			requirementSummary(req({ type: 'span', startDay: 0, startTime: '09:00', endDay: 2, endTime: '17:00', weeksLater: 0 }))
		).toBe('A few days · Mon 09:00 → Wed 17:00 · Nov – Jan');
	});

	test('day counts', () => {
		expect(requirementSummary(req({ type: 'days', count: 7 }, { days: [0, 1, 2, 3, 4, 5, 6] }))).toBe('A week · any day · Nov – Jan');
		expect(requirementSummary(req({ type: 'days', count: 1 }, { days: [4, 5, 6] }))).toBe('One day · Fri–Sun · Nov – Jan');
		expect(requirementSummary(req({ type: 'days', count: 1 }, { window: { from: '18:00', to: '23:59' }, days: [0, 2] }))).toBe(
			'An evening from 18:00 · Mon, Wed · Nov – Jan'
		);
		expect(requirementSummary(req({ type: 'days', count: 3 }, { window: { from: '09:00', to: '17:00' }, days: [5, 6] }))).toBe(
			'3 days · Sat, Sun · Nov – Jan'
		);
	});

	test('durations, extras and single-month ranges', () => {
		expect(requirementSummary(req({ type: 'duration', minutes: 60 }))).toBe('1 hour · any day · Nov – Jan');
		expect(requirementSummary(req({ type: 'duration', minutes: 90 }, { range: { from: '2026-11-01', to: '2026-11-30' } }))).toBe(
			'1.5 hours · any day · Nov'
		);
		expect(
			requirementSummary(
				req({ type: 'duration', minutes: 120 }, { schoolHolidaysOnly: true, dates: [{ from: '2026-12-01', to: '2026-12-02' }] })
			)
		).toBe('2 hours · any day · school holidays · chosen dates · Nov – Jan');
	});

	test('the same month in different years is a span', () => {
		expect(requirementSummary(req({ type: 'duration', minutes: 120 }, { range: { from: '2026-11-01', to: '2027-11-01' } }))).toBe(
			'2 hours · any day · Nov 2026 – Nov 2027'
		);
	});
});

describe('slotNoun', () => {
	test('per shape', () => {
		expect(slotNoun(req(WEEKEND))).toBe('weekend');
		expect(slotNoun(req({ ...WEEKEND, weeksLater: 1 } as RequirementLength))).toBe('trip');
		expect(slotNoun(req({ type: 'days', count: 7 }))).toBe('week');
		expect(slotNoun(req({ type: 'days', count: 1 }))).toBe('day');
		expect(slotNoun(req({ type: 'days', count: 1 }, { window: { from: '18:00', to: '23:59' } }))).toBe('evening');
		expect(slotNoun(req({ type: 'days', count: 3 }))).toBe('trip');
		expect(slotNoun(req({ type: 'duration', minutes: 120 }))).toBe('slot');
	});
});
