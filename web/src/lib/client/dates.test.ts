import { describe, expect, test } from 'bun:test';
import {
	addDays,
	addMonths,
	browserTimeZone,
	daysBetween,
	dayKeyIn,
	eachDay,
	endOfMonth,
	minutesIntoDay,
	minutesToTime,
	overlaps,
	startOfMonth,
	startOfWeek,
	timeIn,
	timeToMinutes,
	weekdayOf,
	zonedToIso
} from './dates';

describe('date keys', () => {
	test('day and month arithmetic', () => {
		expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
		expect(addDays('2026-03-29', 1)).toBe('2026-03-30');
		expect(addMonths('2026-11-13', 1)).toBe('2026-12-01');
		expect(addMonths('2026-11-30', 3)).toBe('2027-02-01');
		expect(addMonths('2026-01-31', -1)).toBe('2025-12-01');
		expect(startOfMonth('2026-11-13')).toBe('2026-11-01');
		expect(endOfMonth('2028-02-10')).toBe('2028-02-29');
		expect(endOfMonth('2026-12-10')).toBe('2026-12-31');
	});

	test('weeks start on Monday', () => {
		expect(weekdayOf('2026-11-08')).toBe(6);
		expect(startOfWeek('2026-11-08')).toBe('2026-11-02');
		expect(startOfWeek('2026-11-02')).toBe('2026-11-02');
	});

	test('ranges', () => {
		expect(daysBetween('2026-10-24', '2026-10-26')).toBe(2);
		expect(eachDay('2026-11-29', '2026-12-01')).toEqual(['2026-11-29', '2026-11-30', '2026-12-01']);
		expect(eachDay('2026-12-01', '2026-11-29')).toEqual([]);
	});
});

describe('zoned conversions', () => {
	test('dayKeyIn accepts strings and Dates', () => {
		expect(dayKeyIn('2026-11-13T23:30:00Z', 'Asia/Tokyo')).toBe('2026-11-14');
		expect(dayKeyIn(new Date('2026-11-13T23:30:00Z'), 'America/New_York')).toBe('2026-11-13');
	});

	test('timeIn', () => {
		expect(timeIn('2026-07-01T23:30:00Z', 'Europe/London')).toBe('00:30');
		expect(timeIn('2026-11-13T00:00:00Z', 'UTC')).toBe('00:00');
	});

	test('zonedToIso across DST', () => {
		expect(zonedToIso('2026-11-13', '18:00', 'Europe/London')).toBe('2026-11-13T18:00:00.000Z');
		expect(zonedToIso('2026-07-13', '18:00', 'Europe/London')).toBe('2026-07-13T17:00:00.000Z');
		expect(zonedToIso('2026-03-30', '00:00', 'Europe/London')).toBe('2026-03-29T23:00:00.000Z');
		expect(zonedToIso('2026-10-25', '12:00', 'Europe/London')).toBe('2026-10-25T12:00:00.000Z');
		expect(zonedToIso('2026-11-13', '09:30', 'Asia/Kolkata')).toBe('2026-11-13T04:00:00.000Z');
	});

	test('minutesIntoDay may fall outside 0–1440', () => {
		expect(minutesIntoDay('2026-11-13T18:30:00Z', '2026-11-13', 'Europe/London')).toBe(18 * 60 + 30);
		expect(minutesIntoDay('2026-11-12T23:00:00Z', '2026-11-13', 'Europe/London')).toBe(-60);
		expect(minutesIntoDay('2026-11-14T01:00:00Z', '2026-11-13', 'Europe/London')).toBe(25 * 60);
		// The spring-forward day is only 23 hours long.
		expect(minutesIntoDay('2026-03-29T23:00:00Z', '2026-03-29', 'Europe/London')).toBe(23 * 60);
	});
});

describe('times', () => {
	test('round trips HH:MM and wraps past midnight', () => {
		expect(timeToMinutes('18:45')).toBe(1125);
		expect(minutesToTime(1125)).toBe('18:45');
		expect(minutesToTime(0)).toBe('00:00');
		expect(minutesToTime(24 * 60)).toBe('00:00');
	});

	test('overlaps is half-open', () => {
		const a = ['2026-11-13T18:00:00Z', '2026-11-13T20:00:00Z'] as const;
		expect(overlaps(...a, '2026-11-13T19:00:00Z', '2026-11-13T21:00:00Z')).toBe(true);
		expect(overlaps(...a, '2026-11-13T20:00:00Z', '2026-11-13T21:00:00Z')).toBe(false);
		expect(overlaps(...a, '2026-11-13T17:00:00Z', '2026-11-13T18:00:00Z')).toBe(false);
		expect(overlaps(...a, '2026-11-13T17:00:00Z', '2026-11-13T22:00:00Z')).toBe(true);
	});

	test('browserTimeZone is a valid zone', () => {
		expect(() => new Intl.DateTimeFormat('en', { timeZone: browserTimeZone() })).not.toThrow();
	});
});
