import { describe, expect, test } from 'bun:test';
import {
	addDays,
	dateOf,
	daysBetween,
	eachDate,
	HOUR,
	inRange,
	minutesOf,
	offsetAt,
	startOfDate,
	wallClock,
	weekdayOf,
	zonedInstant
} from './time';

const iso = (ms: number) => new Date(ms).toISOString();

describe('wallClock / offsetAt', () => {
	test('reads the local time in a zone', () => {
		expect(wallClock(Date.parse('2026-07-01T23:30:45Z'), 'Europe/London')).toEqual({
			year: 2026,
			month: 7,
			day: 2,
			hour: 0,
			minute: 30,
			second: 45
		});
	});

	test('offsets follow DST', () => {
		expect(offsetAt(Date.parse('2026-01-15T12:00:00Z'), 'Europe/London')).toBe(0);
		expect(offsetAt(Date.parse('2026-07-15T12:00:00Z'), 'Europe/London')).toBe(HOUR);
		expect(offsetAt(Date.parse('2026-07-15T12:00:00Z'), 'America/New_York')).toBe(-4 * HOUR);
		expect(offsetAt(Date.parse('2026-07-15T12:00:00Z'), 'Asia/Kolkata')).toBe(5.5 * HOUR);
	});

	test('ignores sub-second precision', () => {
		expect(offsetAt(Date.parse('2026-07-15T12:00:00.999Z'), 'UTC')).toBe(0);
	});
});

describe('minutesOf', () => {
	test('parses HH:MM', () => {
		expect(minutesOf('00:00')).toBe(0);
		expect(minutesOf('18:30')).toBe(18 * 60 + 30);
		expect(minutesOf('23:59')).toBe(23 * 60 + 59);
	});

	test('23:59 as an end time means end of day', () => {
		expect(minutesOf('23:59', { asEnd: true })).toBe(24 * 60);
		expect(minutesOf('23:30', { asEnd: true })).toBe(23 * 60 + 30);
	});
});

describe('zonedInstant', () => {
	test('local wall time to UTC', () => {
		expect(iso(zonedInstant('2026-11-13', 18 * 60, 'Europe/London'))).toBe('2026-11-13T18:00:00.000Z');
		expect(iso(zonedInstant('2026-07-13', 18 * 60, 'Europe/London'))).toBe('2026-07-13T17:00:00.000Z');
		expect(iso(zonedInstant('2026-07-13', 9 * 60, 'Australia/Sydney'))).toBe('2026-07-12T23:00:00.000Z');
	});

	test('minutes past 24h roll into the next day', () => {
		expect(iso(zonedInstant('2026-11-13', 24 * 60, 'UTC'))).toBe('2026-11-14T00:00:00.000Z');
	});

	test('midnight either side of the spring-forward day', () => {
		// UK clocks go forward at 01:00 UTC on 29 March 2026.
		expect(iso(startOfDate('2026-03-29', 'Europe/London'))).toBe('2026-03-29T00:00:00.000Z');
		expect(iso(startOfDate('2026-03-30', 'Europe/London'))).toBe('2026-03-29T23:00:00.000Z');
		expect(iso(zonedInstant('2026-03-29', 12 * 60, 'Europe/London'))).toBe('2026-03-29T11:00:00.000Z');
	});

	test('the hour after falling back', () => {
		// UK clocks go back at 01:00 UTC on 25 October 2026.
		expect(iso(zonedInstant('2026-10-25', 12 * 60, 'Europe/London'))).toBe('2026-10-25T12:00:00.000Z');
		expect(iso(startOfDate('2026-10-25', 'Europe/London'))).toBe('2026-10-24T23:00:00.000Z');
	});

	test('round trips with dateOf/wallClock away from transitions', () => {
		for (const tz of ['Europe/London', 'America/Los_Angeles', 'Asia/Tokyo', 'Pacific/Auckland']) {
			const at = zonedInstant('2026-11-13', 7 * 60 + 15, tz);
			expect(dateOf(at, tz)).toBe('2026-11-13');
			expect(wallClock(at, tz)).toMatchObject({ hour: 7, minute: 15 });
		}
	});
});

describe('date arithmetic', () => {
	test('addDays crosses months, years and leap days', () => {
		expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
		expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
		expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
		expect(addDays('2026-11-13', 0)).toBe('2026-11-13');
	});

	test('weekdayOf is Monday-based', () => {
		expect(weekdayOf('2026-11-02')).toBe(0);
		expect(weekdayOf('2026-11-08')).toBe(6);
		expect(weekdayOf('2026-11-13')).toBe(4);
	});

	test('daysBetween ignores DST', () => {
		expect(daysBetween('2026-03-28', '2026-03-30')).toBe(2);
		expect(daysBetween('2026-11-13', '2026-11-01')).toBe(-12);
	});

	test('eachDate is inclusive and empty for reversed ranges', () => {
		expect(eachDate('2026-12-30', '2027-01-02')).toEqual(['2026-12-30', '2026-12-31', '2027-01-01', '2027-01-02']);
		expect(eachDate('2026-11-13', '2026-11-13')).toEqual(['2026-11-13']);
		expect(eachDate('2026-11-14', '2026-11-13')).toEqual([]);
	});

	test('inRange is inclusive', () => {
		const r = { from: '2026-11-01', to: '2026-11-30' };
		expect(inRange('2026-11-01', r)).toBe(true);
		expect(inRange('2026-11-30', r)).toBe(true);
		expect(inRange('2026-12-01', r)).toBe(false);
		expect(inRange('2026-10-31', r)).toBe(false);
	});
});
