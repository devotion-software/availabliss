import { describe, expect, test } from 'bun:test';
import { nearest, nextTarget } from './rhythm';

describe('nextTarget', () => {
	test('weekly and fortnightly add days', () => {
		expect(nextTarget('2032-03-05', 'week')).toBe('2032-03-12');
		expect(nextTarget('2032-03-05', 'two-weeks')).toBe('2032-03-19');
	});
	test('monthly keeps the weekday and week of the month (first Friday → first Friday)', () => {
		expect(nextTarget('2032-03-05', 'month')).toBe('2032-04-02');
		// The fifth Saturday has no match next month: the last one instead.
		expect(nextTarget('2032-01-31', 'month')).toBe('2032-02-28');
		expect(nextTarget('2032-12-03', 'month')).toBe('2033-01-07');
	});
});

describe('nearest', () => {
	const slot = (day: string, missing: string[] = []) => ({ start: `${day}T18:00:00Z`, end: `${day}T21:00:00Z`, label: day, missingMemberIds: missing });
	test('picks the free time closest to the target, everyone-free first', () => {
		expect(nearest([slot('2032-04-01'), slot('2032-04-05')], '2032-04-02', 'UTC')?.label).toBe('2032-04-01');
		expect(nearest([slot('2032-04-02', ['ana']), slot('2032-04-06')], '2032-04-02', 'UTC')?.label).toBe('2032-04-06');
		expect(nearest([], '2032-04-02', 'UTC')).toBeNull();
	});
});
