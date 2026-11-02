import { describe, expect, test } from 'bun:test';
import type { BusyInterval } from '#lib/types';
import { assignLanes, dayLength, initialScrollMinutes, layoutWeek, rangeToIso, selectionRange, tapRange } from './parts';

const TZ = 'Europe/London';

function busy(start: string, end: string, source: BusyInterval['source'] = 'manual'): BusyInterval {
	return { start, end, source, label: null, calendarId: null, blockId: source === 'manual' ? 'b' : null };
}

describe('layoutWeek', () => {
	// Monday 2 November 2026 (GMT, so UTC = London).
	const week = '2026-11-02';

	test('places intervals at exact minutes on their day', () => {
		const layout = layoutWeek(week, [busy('2026-11-03T19:00:00Z', '2026-11-03T21:30:00Z')], TZ);
		expect(layout.days).toHaveLength(7);
		const tue = layout.timed.get('2026-11-03')!;
		expect(tue).toHaveLength(1);
		expect(tue[0]).toMatchObject({ from: 19 * 60, to: 21 * 60 + 30, lane: 0, lanes: 1, continuesBefore: false, continuesAfter: false });
		expect(layout.timed.get('2026-11-02')).toEqual([]);
	});

	test('splits intervals across midnight', () => {
		const layout = layoutWeek(week, [busy('2026-11-06T22:00:00Z', '2026-11-07T02:00:00Z')], TZ);
		expect(layout.timed.get('2026-11-06')![0]).toMatchObject({ from: 22 * 60, to: 1440, continuesAfter: true });
		expect(layout.timed.get('2026-11-07')![0]).toMatchObject({ from: 0, to: 120, continuesBefore: true });
	});

	test('whole days go to the all-day row', () => {
		const layout = layoutWeek(week, [busy('2026-11-07T00:00:00Z', '2026-11-09T00:00:00Z', 'calendar')], TZ);
		expect(layout.allDay.get('2026-11-07')).toHaveLength(1);
		expect(layout.allDay.get('2026-11-08')).toHaveLength(1);
		expect(layout.timed.get('2026-11-07')).toEqual([]);
	});

	test('uses the user time zone', () => {
		const layout = layoutWeek(week, [busy('2026-11-03T09:00:00Z', '2026-11-03T10:00:00Z')], 'America/New_York');
		expect(layout.timed.get('2026-11-03')![0]).toMatchObject({ from: 4 * 60, to: 5 * 60 });
	});

	test('DST days keep wall-clock positions on the 24-hour axis', () => {
		// 25 October 2026: clocks go back in London, so the day is 25 hours long.
		expect(dayLength('2026-10-25', TZ)).toBe(1500);
		const layout = layoutWeek('2026-10-19', [busy('2026-10-25T23:00:00Z', '2026-10-25T23:59:00Z')], TZ);
		const sun = layout.timed.get('2026-10-25')![0];
		// 23:00 UTC is 23:00 GMT, the 24th hour of a 25-hour day: scaled to 23:00 on the axis.
		expect(Math.round(sun.from)).toBe(Math.round(1440 * (24 / 25)));
	});
});

describe('assignLanes', () => {
	test('overlaps share a cluster lane count, back-to-back items do not', () => {
		const out = assignLanes([
			{ id: 'a', from: 60, to: 180 },
			{ id: 'b', from: 120, to: 240 },
			{ id: 'c', from: 200, to: 260 },
			{ id: 'd', from: 260, to: 300 }
		]);
		const by = Object.fromEntries(out.map((o) => [o.id, o]));
		expect([by.a.lane, by.b.lane, by.c.lane]).toEqual([0, 1, 0]);
		expect([by.a.lanes, by.b.lanes, by.c.lanes]).toEqual([2, 2, 2]);
		expect(by.d).toMatchObject({ lane: 0, lanes: 1 });
	});
});

describe('ranges', () => {
	test('selectionRange snaps outwards, either direction, at least 15 minutes', () => {
		expect(selectionRange(605, 668)).toEqual({ from: 600, to: 675 });
		expect(selectionRange(668, 605)).toEqual({ from: 600, to: 675 });
		expect(selectionRange(600, 600)).toEqual({ from: 600, to: 615 });
		expect(selectionRange(1430, 1500)).toEqual({ from: 1425, to: 1440 });
	});

	test('tapRange suggests the hour tapped', () => {
		expect(tapRange(14 * 60 + 40)).toEqual({ from: 840, to: 900 });
		expect(tapRange(1439)).toEqual({ from: 1380, to: 1440 });
	});

	test('rangeToIso maps 24:00 to the next midnight', () => {
		expect(rangeToIso('2026-11-03', 18 * 60, 1440, TZ)).toEqual({ start: '2026-11-03T18:00:00.000Z', end: '2026-11-04T00:00:00.000Z' });
	});

	test('initialScrollMinutes starts an hour before the earliest item, no later than 06:00', () => {
		const empty = layoutWeek('2026-11-02', [], TZ);
		expect(initialScrollMinutes(empty)).toBe(6 * 60);
		const early = layoutWeek('2026-11-02', [busy('2026-11-04T05:30:00Z', '2026-11-04T06:00:00Z')], TZ);
		expect(initialScrollMinutes(early)).toBe(4 * 60);
	});
});
