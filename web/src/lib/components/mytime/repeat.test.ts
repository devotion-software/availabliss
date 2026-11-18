import { describe, expect, test } from 'bun:test';
import type { ManualBlock } from '#lib/types';
import { blockSummary } from './parts';
import { ordinal, repeatChoices, repeatLabel, scheduleLead, weekdayOfMonth } from './repeat';

const TZ = 'Europe/London';

describe('repeat labels', () => {
	test('ordinal', () => {
		expect([1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 31].map(ordinal)).toEqual([
			'1st',
			'2nd',
			'3rd',
			'4th',
			'11th',
			'12th',
			'13th',
			'21st',
			'22nd',
			'23rd',
			'31st'
		]);
	});

	test('weekdayOfMonth: nth, or last in the final 7 days', () => {
		expect(weekdayOfMonth('2026-11-16')).toBe('3rd Monday');
		expect(weekdayOfMonth('2026-11-02')).toBe('1st Monday');
		// 23 Nov is the 4th Monday and within the last 7 days of November? 30 - 7 = 23, so not yet.
		expect(weekdayOfMonth('2026-11-23')).toBe('4th Monday');
		expect(weekdayOfMonth('2026-11-24')).toBe('last Tuesday');
		expect(weekdayOfMonth('2026-11-30')).toBe('last Monday');
		// February 2027 has 28 days: the 22nd onwards is the last week.
		expect(weekdayOfMonth('2027-02-22')).toBe('last Monday');
	});

	test('repeatChoices are built from the start date', () => {
		expect(repeatChoices('2026-11-16').map((c) => c.label)).toEqual([
			'Never',
			'Every day',
			'Every weekday (Mon–Fri)',
			'Every week on Monday',
			'Every 2 weeks on Monday',
			'Every month on the 16th',
			'Every month on the 3rd Monday',
			'Every year on 16 November'
		]);
		expect(repeatChoices('2026-11-30').find((c) => c.value === 'monthlyWeekday')!.label).toBe('Every month on the last Monday');
		expect(repeatChoices('2026-11-16').map((c) => c.value)).toEqual([
			'none',
			'daily',
			'weekdays',
			'weekly',
			'fortnightly',
			'monthly',
			'monthlyWeekday',
			'yearly'
		]);
	});

	test('repeatLabel: unknown values read "Repeats"', () => {
		expect(repeatLabel('weekly', '2026-11-17')).toBe('Every week on Tuesday');
		expect(repeatLabel('hourly', '2026-11-17')).toBe('Repeats');
	});

	test('scheduleLead', () => {
		const day = '2026-11-16';
		expect(scheduleLead('none', day)).toBeNull();
		expect(scheduleLead('daily', day)).toBe('Every day');
		expect(scheduleLead('weekdays', day)).toBe('Weekdays');
		expect(scheduleLead('weekly', day)).toBe('Every Monday');
		expect(scheduleLead('fortnightly', day)).toBe('Every other Monday');
		expect(scheduleLead('monthly', day)).toBe('Monthly on the 16th');
		expect(scheduleLead('monthlyWeekday', day)).toBe('Monthly on the 3rd Monday');
		expect(scheduleLead('yearly', day)).toBe('Yearly on 16 Nov');
		expect(scheduleLead('hourly', day)).toBe('Repeats');
	});
});

describe('blockSummary', () => {
	const block = (over: Partial<ManualBlock>): ManualBlock => ({
		id: 'b',
		start: '2026-11-17T19:00:00Z',
		end: '2026-11-17T21:30:00Z',
		allDay: false,
		repeat: 'none',
		until: null,
		note: null,
		...over
	});

	test('one-offs show their date', () => {
		expect(blockSummary(block({}), TZ)).toBe('Tue 17 Nov · 19:00 – 21:30');
	});

	test('repeats lead with the schedule, then the time and any end', () => {
		expect(blockSummary(block({ repeat: 'weekly' }), TZ)).toBe('Every Tuesday · 19:00 – 21:30');
		expect(blockSummary(block({ repeat: 'daily', start: '2026-11-17T07:00:00Z', end: '2026-11-17T08:00:00Z' }), TZ)).toBe(
			'Every day · 07:00 – 08:00'
		);
		expect(blockSummary(block({ repeat: 'monthlyWeekday', until: '2026-12-20' }), TZ)).toBe(
			'Monthly on the 3rd Tuesday · 19:00 – 21:30 · until Sun 20 Dec'
		);
		expect(blockSummary(block({ repeat: 'yearly', allDay: true, start: '2026-11-17T00:00:00Z', end: '2026-11-18T00:00:00Z' }), TZ)).toBe(
			'Yearly on 17 Nov · All day'
		);
	});
});
