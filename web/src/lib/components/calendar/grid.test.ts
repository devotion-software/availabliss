import { describe, expect, test } from 'bun:test';
import type { AvailabilityDay, Member } from '#lib/types';
import { findFreeDay, missingNote, missingOn, startsFreeRun } from './grid';
import { subtractSpans } from './timeline';

const day = (date: string, status: AvailabilityDay['status'], slotPosition: AvailabilityDay['slotPosition'] = null) =>
	({ date, status, slotPosition, busyMemberIds: [], holiday: null }) as AvailabilityDay;

// Weekends that work: 11–13 Dec, 1–3 Jan; nothing in November or February.
const months: Record<string, AvailabilityDay[]> = {
	'2026-11-01': [day('2026-11-20', 'busy'), day('2026-11-21', 'free')],
	'2026-12-01': [day('2026-12-04', 'busy'), day('2026-12-11', 'match', 'start'), day('2026-12-12', 'match', 'middle'), day('2026-12-13', 'match', 'end')],
	'2027-01-01': [day('2027-01-01', 'match', 'start'), day('2027-01-02', 'match', 'middle'), day('2027-01-03', 'match', 'end')],
	'2027-02-01': [day('2027-02-05', 'hidden')]
};
const fetched: string[] = [];
const fetchMonth = async (m: string) => {
	fetched.push(m);
	return { days: months[m] ?? [] };
};

describe('startsFreeRun', () => {
	test('only the first day of a run that works', () => {
		expect(startsFreeRun(day('2026-12-11', 'match', 'start'))).toBe(true);
		expect(startsFreeRun(day('2026-12-11', 'match', 'single'))).toBe(true);
		expect(startsFreeRun(day('2026-12-11', 'match', null))).toBe(true);
		expect(startsFreeRun(day('2026-12-12', 'match', 'middle'))).toBe(false);
		expect(startsFreeRun(day('2026-12-13', 'match', 'end'))).toBe(false);
		expect(startsFreeRun(day('2026-12-04', 'busy'))).toBe(false);
	});
});

describe('findFreeDay', () => {
	test('skips months where nothing works', async () => {
		expect(await findFreeDay(fetchMonth, '2026-12-01', '2027-02-14', true)).toBe('2026-12-11');
		expect(await findFreeDay(fetchMonth, '2026-11-18', '2027-02-14', true)).toBe('2026-12-11');
	});
	test('starts after the page, not in the middle of a weekend already shown', async () => {
		expect(await findFreeDay(fetchMonth, '2026-12-12', '2027-02-14', true)).toBe('2027-01-01');
	});
	test('looks back for the last run that starts in range', async () => {
		expect(await findFreeDay(fetchMonth, '2026-11-18', '2027-01-31', false)).toBe('2027-01-01');
		expect(await findFreeDay(fetchMonth, '2026-11-18', '2026-12-31', false)).toBe('2026-12-11');
	});
	test('none in that direction within the range', async () => {
		expect(await findFreeDay(fetchMonth, '2026-11-18', '2026-11-30', false)).toBeNull();
		expect(await findFreeDay(fetchMonth, '2027-02-01', '2027-02-14', true)).toBeNull();
		expect(await findFreeDay(fetchMonth, '2026-12-01', '2026-11-30', true)).toBeNull();
	});
	test('stops at the end of the range without fetching past it', async () => {
		fetched.length = 0;
		await findFreeDay(fetchMonth, '2027-01-04', '2027-02-14', true);
		expect(fetched).toEqual(['2027-01-01', '2027-02-01']);
	});
	test('a month that fails to load gives up', async () => {
		expect(await findFreeDay(() => Promise.reject(new Error('offline')), '2026-12-01', '2027-02-14', true)).toBeNull();
	});
});

describe('missingNote', () => {
	const members = new Map([
		['a', { userId: 'a', name: 'Ana Silva' }],
		['b', { userId: 'b', name: 'Tom' }]
	] as [string, Member][]);
	test('names one person, counts more', () => {
		expect(missingNote(['a'], members)).toBe('Ana busy');
		expect(missingNote(['a', 'b'], members)).toBe('2 missing');
		expect(missingNote([], members)).toBe('Most free');
	});
	test('missingOn prefers the slot', () => {
		expect(missingOn({ busyMemberIds: ['b'] }, { missingMemberIds: ['a'] })).toEqual(['a']);
		expect(missingOn({ busyMemberIds: ['b'] }, null)).toEqual(['b']);
	});
});

describe('subtractSpans', () => {
	test('cuts holes', () => {
		expect(subtractSpans([{ from: 0, to: 100 }], [{ from: 20, to: 30 }, { from: 90, to: 120 }])).toEqual([
			{ from: 0, to: 20 },
			{ from: 30, to: 90 }
		]);
	});
});
