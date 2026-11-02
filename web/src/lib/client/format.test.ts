import { afterEach, describe, expect, setSystemTime, test } from 'bun:test';
import {
	createFormatter,
	dayNumber,
	firstName,
	formatDayKey,
	formatDayRange,
	formatMonthSpan,
	initials,
	listNames,
	monthShort,
	monthTitle,
	plural,
	weekdayName
} from './format';

afterEach(() => setSystemTime());

describe('date-key formatting', () => {
	test('single days', () => {
		expect(formatDayKey('2026-11-13')).toBe('Fri 13 Nov');
		expect(dayNumber('2026-11-03')).toBe('3');
		expect(weekdayName('2026-11-13')).toBe('Fri');
		expect(weekdayName('2026-11-13', 'long')).toBe('Friday');
		expect(monthTitle('2026-11-13')).toBe('November 2026');
		expect(monthShort('2026-11-13')).toBe('Nov');
	});

	test('ranges collapse the shared month and year', () => {
		expect(formatDayRange('2026-11-13', '2026-11-13')).toBe('Fri 13 Nov');
		expect(formatDayRange('2026-11-13', '2026-11-15')).toBe('Fri 13 – Sun 15 Nov');
		expect(formatDayRange('2026-11-30', '2026-12-02')).toBe('Mon 30 Nov – Wed 2 Dec');
		expect(formatDayRange('2026-12-30', '2027-01-02')).toBe('Wed 30 Dec 2026 – Sat 2 Jan 2027');
		expect(formatDayRange('2026-11-13', '2026-11-15', false)).toBe('13 – 15 Nov');
		expect(formatDayRange('2026-11-30', '2026-12-02', false)).toBe('30 Nov – 2 Dec');
	});

	test('month spans', () => {
		expect(formatMonthSpan('2026-11-01', '2026-11-30')).toBe('Nov');
		expect(formatMonthSpan('2026-11-01', '2027-01-31')).toBe('Nov – Jan');
	});
});

describe('createFormatter', () => {
	const f = createFormatter('Europe/London');

	test('instants in the user zone', () => {
		expect(f.timeZone).toBe('Europe/London');
		expect(f.dayKey('2026-07-01T23:30:00Z')).toBe('2026-07-02');
		expect(f.time('2026-07-01T17:05:00Z')).toBe('18:05');
		expect(f.day('2026-11-11T18:30:00Z')).toBe('Wed 11 Nov');
		expect(f.dayLong('2026-11-01T12:00:00Z')).toBe('Sunday 1 November');
		expect(f.dateTime('2026-11-11T18:30:00Z')).toBe('Wed 11 Nov · 18:30');
	});

	test('when', () => {
		expect(f.when('2026-11-11T18:30:00Z', '2026-11-11T21:00:00Z')).toBe('Wed 11 Nov · 18:30 – 21:00');
		expect(f.when('2026-11-11T18:30:00Z', '2026-11-12T00:00:00Z')).toBe('Wed 11 Nov · 18:30 – midnight');
		expect(f.when('2026-11-13T18:00:00Z', '2026-11-16T00:00:00Z')).toBe('Fri 13 – Sun 15 Nov');
		expect(f.when('2026-11-21T00:00:00Z', '2026-11-22T00:00:00Z', true)).toBe('Sat 21 Nov');
		// A zero-length all-day value never renders a backwards range.
		expect(f.when('2026-11-21T00:00:00Z', '2026-11-21T00:00:00Z', true)).toBe('Sat 21 Nov');
	});

	test('startsAt', () => {
		expect(f.startsAt('2026-11-13T18:00:00Z')).toBe('From Fri 18:00');
		expect(f.startsAt('2026-11-13T00:00:00Z')).toBe('');
	});

	test('relative', () => {
		setSystemTime(new Date('2026-11-13T12:00:00Z'));
		expect(f.today()).toBe('2026-11-13');
		expect(f.relative('2026-11-13T11:59:30Z')).toBe('now');
		expect(f.relative('2026-11-13T11:55:00Z')).toBe('5m ago');
		expect(f.relative('2026-11-13T09:00:00Z')).toBe('3h ago');
		expect(f.relative('2026-11-12T20:00:00Z')).toBe('Yesterday');
		expect(f.relative('2026-11-09T20:00:00Z')).toBe('Mon');
		expect(f.relative('2026-11-03T20:00:00Z')).toBe('3 Nov');
	});

	test('dayDivider', () => {
		setSystemTime(new Date('2026-11-13T12:00:00Z'));
		expect(f.dayDivider('2026-11-13T08:00:00Z')).toBe('Today');
		expect(f.dayDivider('2026-11-12T08:00:00Z')).toBe('Yesterday');
		expect(f.dayDivider('2026-11-02T08:00:00Z')).toBe('Mon 2 Nov');
	});
});

describe('names', () => {
	test('firstName and initials', () => {
		expect(firstName('  Sam Taylor ')).toBe('Sam');
		expect(firstName('')).toBe('');
		expect(initials('sam taylor')).toBe('ST');
		expect(initials('Ana María de la Cruz')).toBe('AC');
		expect(initials('Prince')).toBe('P');
		expect(initials('   ')).toBe('?');
	});

	test('listNames', () => {
		expect(listNames([])).toBe('');
		expect(listNames(['Leo'])).toBe('Leo');
		expect(listNames(['Leo', 'Jade'])).toBe('Leo and Jade');
		expect(listNames(['Ana', 'Priya', 'Tom'])).toBe('Ana, Priya and Tom');
		expect(listNames(['Ana', 'Priya', 'Tom', 'Leo'])).toBe('Ana, Priya, Tom and 1 other');
		expect(listNames(['Ana', 'Priya', 'Tom', 'Leo', 'Jade', 'Sam'])).toBe('Ana, Priya, Tom and 3 others');
		expect(listNames(['A', 'B', 'C'], 2)).toBe('A, B and 1 other');
	});

	test('plural', () => {
		expect(plural(1, 'day')).toBe('1 day');
		expect(plural(0, 'day')).toBe('0 days');
		expect(plural(2, 'person', 'people')).toBe('2 people');
	});
});
