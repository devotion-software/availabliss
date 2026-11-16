import { describe, expect, test } from 'bun:test';
import type { BusyInterval } from '#lib/types';
import type { ManualBlock } from '#lib/types';
import {
	agendaItems,
	busyRanges,
	clampDay,
	dayCellLabel,
	dayLine,
	dayMarks,
	dayNames,
	describeDay,
	eveningPreset,
	freeGaps,
	freeLevel,
	freeMinutes,
	freeSummary,
	groupWeeks,
	longestFree,
	mergeRanges,
	monthCells,
	sourceTag,
	weekHeading,
	type DayPiece
} from './days';

const h = (hours: number, minutes = 0) => hours * 60 + minutes;

function interval(over: Partial<BusyInterval> = {}): BusyInterval {
	return { start: '', end: '', source: 'manual', label: null, calendarId: null, blockId: 'b', ...over };
}
const piece = (from: number, to: number, over: Partial<BusyInterval> = {}): DayPiece => ({ from, to, interval: interval(over) });

describe('ranges', () => {
	test('mergeRanges joins overlapping and touching ranges', () => {
		expect(mergeRanges([{ from: h(10), to: h(12) }, { from: h(9), to: h(10) }, { from: h(14), to: h(15) }, { from: h(11), to: h(13) }])).toEqual([
			{ from: h(9), to: h(13) },
			{ from: h(14), to: h(15) }
		]);
	});

	test('busyRanges skips events marked not busy; an all-day item fills the day', () => {
		expect(busyRanges([piece(h(9), h(10), { source: 'calendar', ignoreId: 'x' }), piece(h(12), h(13))], [])).toEqual([{ from: h(12), to: h(13) }]);
		expect(busyRanges([], [interval({ source: 'calendar' })])).toEqual([{ from: 0, to: 1440 }]);
		expect(busyRanges([], [interval({ source: 'calendar', ignoreId: 'x' })])).toEqual([]);
	});

	test('freeGaps only looks at 06:00 – 24:00', () => {
		expect(freeGaps([{ from: h(0), to: h(7) }, { from: h(20), to: h(22) }])).toEqual([
			{ from: h(7), to: h(20) },
			{ from: h(22), to: h(24) }
		]);
	});
});

describe('freeSummary', () => {
	test('free and busy all day', () => {
		expect(freeSummary([])).toBe('Free all day');
		// Busy only before 06:00 still reads as a free day.
		expect(freeSummary([{ from: h(1), to: h(5) }])).toBe('Free all day');
		expect(freeSummary([{ from: 0, to: 1440 }])).toBe('Busy all day');
		// Nothing free for at least half an hour.
		expect(freeSummary([{ from: h(6), to: h(12) }, { from: h(12, 20), to: h(24) }])).toBe('Busy all day');
	});

	test('the longest free stretch, said the simplest way', () => {
		expect(freeSummary([{ from: h(9), to: h(18) }])).toBe('Free from 18:00');
		expect(freeSummary([{ from: h(9), to: h(24) }])).toBe('Free until 09:00');
		expect(freeSummary([{ from: h(6), to: h(12) }, { from: h(14), to: h(24) }])).toBe('Free 12:00 – 14:00');
		expect(freeSummary([{ from: h(19), to: h(21, 30) }])).toBe('Free until 19:00');
	});
});

describe('describeDay', () => {
	test('reads busy times and the free summary', () => {
		const busy = [
			{ from: h(9), to: h(17) },
			{ from: h(19), to: h(21, 30) }
		];
		expect(describeDay('2026-11-17', busy)).toBe('Tuesday 17 November, busy 09:00 to 17:00 and 19:00 to 21:30, free until 09:00');
		expect(describeDay('2026-11-16', [])).toBe('Monday 16 November, free all day');
		expect(describeDay('2026-11-16', [{ from: 0, to: 1440 }], ['Holiday'])).toBe('Monday 16 November, busy all day, Holiday');
		expect(describeDay('2026-11-16', [{ from: h(9), to: h(17) }])).toBe('Monday 16 November, busy 09:00 to 17:00, free from 17:00');
	});
});

describe('dayNames', () => {
	test('your own block notes, once each, in time order', () => {
		const timed = [piece(h(19), h(21), { label: 'Choir' }), piece(h(9), h(10), { label: 'Gym' }), piece(h(12), h(13), { source: 'calendar', label: 'Work calendar' }), piece(h(22), h(23), { label: 'Choir' })];
		expect(dayNames(timed, [])).toEqual(['Gym', 'Choir']);
	});
});

describe('weeks', () => {
	// Wednesday 18 November 2026; the week starts Monday 16.
	const today = '2026-11-18';
	test('weekHeading', () => {
		expect(weekHeading('2026-11-16', today)).toBe('This week');
		expect(weekHeading('2026-11-23', today)).toBe('Next week');
		expect(weekHeading('2026-11-30', today)).toBe('30 Nov – 6 Dec');
		expect(weekHeading('2026-12-07', today)).toBe('7 – 13 Dec');
	});

	test('groupWeeks starts the first week today', () => {
		const groups = groupWeeks(['2026-11-21', '2026-11-22', '2026-11-23'], '2026-11-21');
		expect(groups.map((g) => [g.heading, g.days.length])).toEqual([
			['This week', 2],
			['Next week', 1]
		]);
	});
});

test('eveningPreset moves to tomorrow after 18:00', () => {
	expect(eveningPreset('2026-11-16', h(17, 59))).toEqual({ day: '2026-11-16', range: { from: h(18), to: h(23, 59) } });
	expect(eveningPreset('2026-11-16', h(18))).toEqual({ day: '2026-11-17', range: { from: h(18), to: h(23, 59) } });
});

describe('how free a day is', () => {
	test('freeMinutes counts 06:00 – 24:00 only', () => {
		expect(freeMinutes([])).toBe(1080);
		expect(freeMinutes([{ from: 0, to: 1440 }])).toBe(0);
		expect(freeMinutes([{ from: h(1), to: h(7) }])).toBe(1080 - 60);
		// Overlaps aren't counted twice.
		expect(freeMinutes([{ from: h(9), to: h(12) }, { from: h(10), to: h(13) }])).toBe(1080 - 240);
	});

	test('freeLevel thresholds: 12h, 6h, 30m', () => {
		expect(freeLevel([])).toBe(3);
		expect(freeLevel([{ from: h(6), to: h(12) }])).toBe(3); // exactly 12h free
		expect(freeLevel([{ from: h(6), to: h(12, 1) }])).toBe(2);
		expect(freeLevel([{ from: h(6), to: h(18) }])).toBe(2); // exactly 6h free
		expect(freeLevel([{ from: h(6), to: h(18, 1) }])).toBe(1);
		expect(freeLevel([{ from: h(6), to: h(23, 30) }])).toBe(1); // exactly 30m free
		expect(freeLevel([{ from: h(6), to: h(23, 31) }])).toBe(0);
		expect(freeLevel([{ from: 0, to: 1440 }])).toBe(0);
	});

	test('longestFree finds the stretch the summary describes', () => {
		expect(longestFree([{ from: h(9), to: h(17, 30) }])).toEqual({ from: h(17, 30), to: h(24) });
		expect(longestFree([])).toEqual({ from: h(6), to: h(24) });
		expect(longestFree([{ from: 0, to: 1440 }])).toBeNull();
	});
});

describe('labels', () => {
	test('dayCellLabel says the date, today and the free summary', () => {
		expect(dayCellLabel('2026-11-23', '2026-11-16', [{ from: h(9), to: h(17, 30) }])).toBe('Monday 23 November, free from 17:30');
		expect(dayCellLabel('2026-11-16', '2026-11-16', [{ from: h(6), to: h(12) }, { from: h(14), to: h(24) }])).toBe('Monday 16 November, today, free 12:00 to 14:00');
		expect(dayCellLabel('2026-11-16', '2026-11-17', null)).toBe('Monday 16 November');
	});

	test('dayLine', () => {
		expect(dayLine('2026-11-16', '2026-11-16', [{ from: h(9), to: h(17, 30) }], ['Choir'])).toBe('Today · Free from 17:30 · Choir');
		expect(dayLine('2026-11-18', '2026-11-16', [])).toBe('Wednesday 18 November · Free all day');
	});
});

test('dayMarks', () => {
	expect(dayMarks([piece(h(9), h(10))], [])).toEqual({ mine: true, calendar: false });
	expect(dayMarks([piece(h(9), h(10), { source: 'calendar' })], [])).toEqual({ mine: false, calendar: true });
	expect(dayMarks([piece(h(9), h(10), { source: 'calendar', ignoreId: 'x' })], [interval()])).toEqual({ mine: true, calendar: false });
});

test('monthCells starts on Monday', () => {
	// 1 November 2026 is a Sunday: six blanks first.
	const cells = monthCells('2026-11-01');
	expect(cells.slice(0, 7)).toEqual([null, null, null, null, null, null, '2026-11-01']);
	expect(cells.length).toBe(6 + 30);
	expect(cells.at(-1)).toBe('2026-11-30');
	// 1 February 2027 is a Monday.
	expect(monthCells('2027-02-14')[0]).toBe('2027-02-01');
});

describe('agenda', () => {
	const block = (id: string, repeat: ManualBlock['repeat']): ManualBlock => ({ id, start: '', end: '', allDay: false, repeat, until: null, note: null });
	const blocks = [block('w', 'weekly'), block('f', 'fortnightly'), block('o', 'none')];

	test('sourceTag', () => {
		expect(sourceTag(interval({ blockId: 'w' }), blocks)).toBe('Weekly');
		expect(sourceTag(interval({ blockId: 'f' }), blocks)).toBe('Every other week');
		expect(sourceTag(interval({ blockId: 'o' }), blocks)).toBe('Your block');
		expect(sourceTag(interval({ source: 'calendar', blockId: null }), blocks)).toBe('Calendar');
	});

	test('agendaItems: all day first, then by time, not-busy events left out', () => {
		const items = agendaItems(
			[piece(h(19), h(21), { label: 'Choir', blockId: 'w' }), piece(h(9), h(17), { source: 'calendar', label: 'Work calendar', blockId: null }), piece(h(12), h(13), { source: 'calendar', ignoreId: 'x' })],
			[interval({ label: 'Holiday', blockId: 'o' })],
			blocks
		);
		expect(items.map((i) => [i.title, i.range && [i.range.from, i.range.to], i.tag])).toEqual([
			['Holiday', null, 'Your block'],
			['Busy', [h(9), h(17)], 'Calendar'],
			['Choir', [h(19), h(21)], 'Weekly']
		]);
	});
});

test('clampDay', () => {
	expect(clampDay('2026-11-10', '2026-11-16', '2027-11-16')).toBe('2026-11-16');
	expect(clampDay('2027-12-01', '2026-11-16', '2027-11-16')).toBe('2027-11-16');
	expect(clampDay('2026-12-01', '2026-11-16', '2027-11-16')).toBe('2026-12-01');
});
