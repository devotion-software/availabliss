import { describe, expect, test } from 'bun:test';
import type { Requirement } from '#lib/types';
import { computeAvailability, computeDay, type EngineInput, type MemberBusy } from './engine';
import { requirementSummary, slotLabel } from './labels';
import { weekdayOf, zonedInstant } from './time';

const TZ = 'Europe/London';
const at = (iso: string) => Date.parse(iso);
const busy = (start: string, end: string, manual = false) => ({ start: at(start), end: at(end), manual });

const weekend: Requirement = {
	length: { type: 'span', startDay: 4, startTime: '18:00', endDay: 6, endTime: '23:59', weeksLater: 0 },
	window: null,
	days: [],
	bankHolidays: false,
	schoolHolidaysOnly: false,
	schoolAuthority: null,
	customSchoolHolidays: [],
	range: { from: '2026-11-01', to: '2027-01-31' },
	dates: [],
	timezone: TZ
};

const members: MemberBusy[] = [
	{ userId: 'sam', synced: true, busy: [busy('2026-11-13T09:00:00Z', '2026-11-13T17:30:00Z')] },
	{ userId: 'priya', synced: true, busy: [busy('2026-11-07T00:00:00Z', '2026-11-08T00:00:00Z', true)] },
	{ userId: 'tom', synced: true, busy: [busy('2026-11-20T17:00:00Z', '2026-11-20T23:00:00Z')] },
	{ userId: 'leo', synced: false, busy: [] }
];

function input(overrides: Partial<EngineInput> = {}): EngineInput {
	return {
		requirement: weekend,
		from: '2026-11-01',
		to: '2026-11-30',
		members,
		bankHolidays: new Map(),
		schoolHolidays: [],
		now: at('2026-11-01T12:00:00Z'),
		...overrides
	};
}

const dayOf = (result: ReturnType<typeof computeAvailability>, date: string) => result.days.find((d) => d.date === date)!;

describe('weekend span', () => {
	const result = computeAvailability(input());

	test('finds the weekends where every synced member is free', () => {
		expect(result.slots.map((s) => s.label)).toEqual(['Fri 13 – Sun 15 Nov', 'Fri 27 – Sun 29 Nov']);
	});

	test('busy outside the slot does not block it (Sam works Friday daytime)', () => {
		expect(dayOf(result, '2026-11-13').status).toBe('match');
	});

	test('joins multi-day slots into one band', () => {
		expect(['2026-11-13', '2026-11-14', '2026-11-15'].map((d) => dayOf(result, d).slotPosition)).toEqual(['start', 'middle', 'end']);
	});

	test('marks days someone is busy', () => {
		expect(dayOf(result, '2026-11-07')).toMatchObject({ status: 'busy', busyMemberIds: ['priya'] });
		expect(dayOf(result, '2026-11-20')).toMatchObject({ status: 'busy', busyMemberIds: ['tom'] });
	});

	test('free days in a weekend that does not work are plain free', () => {
		expect(dayOf(result, '2026-11-21').status).toBe('free');
	});

	test('hides weekdays that can never match and only shows Fri–Sun columns', () => {
		expect(dayOf(result, '2026-11-10').status).toBe('hidden');
		expect(result.visibleWeekdays).toEqual([4, 5, 6]);
	});

	test('ignores members who have not synced and reports them', () => {
		expect(result.syncedCount).toBe(3);
		expect(result.pendingMemberIds).toEqual(['leo']);
	});

	test('reports synced members whose calendars stop before the end of the range', () => {
		expect(result.checkedUntil).toEqual([]);
		const gaps = computeAvailability(
			input({
				members: [
					{ ...members[0], checkedUntil: at('2026-11-18T00:00:00Z') },
					// Known to the last moment of the range (midnight after the 30th, London time): no gap.
					{ ...members[1], checkedUntil: at('2026-12-01T00:00:00Z') },
					{ ...members[2], checkedUntil: null },
					// Not synced: already reported as pending.
					{ ...members[3], checkedUntil: at('2026-11-02T00:00:00Z') }
				]
			})
		);
		expect(gaps.checkedUntil).toEqual([{ userId: 'sam', until: '2026-11-18T00:00:00.000Z' }]);
	});

	test('hides weekends that start before the search range', () => {
		expect(dayOf(result, '2026-11-01').status).toBe('hidden');
	});

	test('skips slots that already started', () => {
		const early = computeAvailability(input({ requirement: { ...weekend, range: { from: '2026-10-01', to: '2027-01-31' } } }));
		expect(dayOf(early, '2026-11-01').status).toBe('free');
	});
});

describe('span over several weeks', () => {
	test('Sat → Sun two weeks later is a 16-day slot', () => {
		const requirement: Requirement = {
			...weekend,
			length: { type: 'span', startDay: 5, startTime: '00:00', endDay: 6, endTime: '23:59', weeksLater: 2 }
		};
		const result = computeAvailability(input({ requirement, members: [{ userId: 'sam', synced: true, busy: [] }] }));
		const first = result.slots[0];
		expect(first.label).toBe('Sat 7 – Sun 22 Nov');
	});
});

describe('duration inside a daily window', () => {
	const req: Requirement = {
		...weekend,
		length: { type: 'duration', minutes: 120 },
		window: { from: '18:00', to: '22:00' },
		days: [1, 3]
	};
	const result = computeAvailability(
		input({
			requirement: req,
			from: '2026-11-02',
			to: '2026-11-08',
			members: [
				{ userId: 'a', synced: true, busy: [busy('2026-11-03T18:00:00Z', '2026-11-03T19:30:00Z')] },
				{ userId: 'b', synced: true, busy: [busy('2026-11-05T18:00:00Z', '2026-11-05T21:00:00Z')] }
			]
		})
	);

	test('reports free stretches at least as long as the duration', () => {
		expect(result.slots.map((s) => s.label)).toEqual(['Tue 3 Nov · 19:30–22:00']);
	});

	test('a day without a long enough stretch is busy', () => {
		expect(dayOf(result, '2026-11-05').status).toBe('busy');
	});

	test('only allowed weekdays are visible', () => {
		expect(result.visibleWeekdays).toEqual([1, 3]);
		expect(dayOf(result, '2026-11-04').status).toBe('hidden');
	});
});

describe('whole evenings', () => {
	const req: Requirement = { ...weekend, length: { type: 'days', count: 1 }, window: { from: '18:00', to: '23:59' }, days: [0, 1, 2, 3, 4] };

	test('daytime busy time does not block an evening', () => {
		const result = computeAvailability(
			input({
				requirement: req,
				from: '2026-11-02',
				to: '2026-11-02',
				members: [{ userId: 'a', synced: true, busy: [busy('2026-11-02T09:00:00Z', '2026-11-02T17:00:00Z')] }]
			})
		);
		expect(result.slots.map((s) => s.label)).toEqual(['Mon 2 Nov · from 18:00']);
	});
});

describe('holidays', () => {
	const req: Requirement = { ...weekend, length: { type: 'days', count: 1 }, days: [5, 6], bankHolidays: true };

	test('bank holidays count as allowed days', () => {
		const result = computeAvailability(
			input({
				requirement: req,
				from: '2026-12-21',
				to: '2026-12-31',
				members: [],
				bankHolidays: new Map([['2026-12-25', 'Christmas Day']])
			})
		);
		expect(dayOf(result, '2026-12-25')).toMatchObject({ status: 'match', holiday: 'Christmas Day' });
		expect(result.visibleWeekdays).toContain(weekdayOf('2026-12-25'));
	});

	test('school-holidays-only restricts to the given ranges', () => {
		const result = computeAvailability(
			input({
				requirement: { ...req, bankHolidays: false, schoolHolidaysOnly: true },
				from: '2026-12-12',
				to: '2026-12-27',
				members: [],
				schoolHolidays: [{ from: '2026-12-19', to: '2027-01-04', name: 'Christmas holidays' }]
			})
		);
		expect(dayOf(result, '2026-12-13').status).toBe('hidden');
		expect(dayOf(result, '2026-12-19')).toMatchObject({ status: 'match', holiday: 'Christmas holidays' });
	});
});

describe('day view', () => {
	test('returns the requirement window and who is busy', () => {
		const day = computeDay(input(), '2026-11-13');
		expect(day.window).toEqual({ start: '2026-11-13T18:00:00.000Z', end: '2026-11-14T00:00:00.000Z' });
		expect(day.members.find((m) => m.userId === 'sam')!.busy).toHaveLength(1);
		expect(day.members.find((m) => m.userId === 'leo')!.pending).toBe(true);
		expect(day.everyoneFree).toEqual([{ start: '2026-11-13T18:00:00.000Z', end: '2026-11-14T00:00:00.000Z' }]);
	});

	test("merges busy time and doesn't say what was blocked by hand", () => {
		const overlapping: MemberBusy[] = [
			{
				userId: 'sam',
				synced: true,
				busy: [busy('2026-11-13T09:00:00Z', '2026-11-13T12:00:00Z'), busy('2026-11-13T11:00:00Z', '2026-11-13T14:00:00Z', true)]
			}
		];
		const day = computeDay(input({ members: overlapping }), '2026-11-13');
		expect(day.members[0].busy).toEqual([{ start: '2026-11-13T09:00:00.000Z', end: '2026-11-13T14:00:00.000Z', manual: false }]);
	});
});

describe('time and labels', () => {
	test('converts wall-clock times across the BST change', () => {
		expect(new Date(zonedInstant('2026-10-24', 12 * 60, TZ)).toISOString()).toBe('2026-10-24T11:00:00.000Z');
		expect(new Date(zonedInstant('2026-10-26', 12 * 60, TZ)).toISOString()).toBe('2026-10-26T12:00:00.000Z');
	});

	test('labels slots that span months', () => {
		const span = { start: at('2026-11-27T18:00:00Z'), end: at('2026-12-02T00:00:00Z') };
		expect(slotLabel(span, weekend)).toBe('Fri 27 Nov – Tue 1 Dec');
	});

	test('summarises requirements', () => {
		expect(requirementSummary(weekend)).toBe('A weekend · Fri 18:00 → Sun · Nov – Jan');
		expect(requirementSummary({ ...weekend, length: { type: 'duration', minutes: 120 }, days: [0, 1, 2, 3, 4] })).toBe(
			'2 hours · Mon–Fri · Nov – Jan'
		);
	});
});

describe('most of us are free (quorum and optional members)', () => {
	test('a quorum adds the weekends one person misses, marked partial with who’s missing', () => {
		const result = computeAvailability(input({ requirement: { ...weekend, quorum: 2 } }));
		expect(result.slots.map((s) => s.label)).toEqual(['Fri 6 – Sun 8 Nov', 'Fri 13 – Sun 15 Nov', 'Fri 20 – Sun 22 Nov', 'Fri 27 – Sun 29 Nov']);
		const byLabel = Object.fromEntries(result.slots.map((s) => [s.label, s.missingMemberIds]));
		expect(byLabel['Fri 6 – Sun 8 Nov']).toEqual(['priya']);
		expect(byLabel['Fri 20 – Sun 22 Nov']).toEqual(['tom']);
		expect(byLabel['Fri 13 – Sun 15 Nov']).toEqual([]);
		expect(dayOf(result, '2026-11-07')).toMatchObject({ status: 'match', partial: true });
		expect(dayOf(result, '2026-11-14').partial).toBeUndefined();
	});

	test('an optional member never stops a time, but shows as missing', () => {
		const result = computeAvailability(input({ requirement: { ...weekend, optionalMemberIds: ['tom'] } }));
		const twentieth = result.slots.find((s) => s.label === 'Fri 20 – Sun 22 Nov');
		expect(twentieth?.missingMemberIds).toEqual(['tom']);
		expect(result.slots.some((s) => s.label === 'Fri 6 – Sun 8 Nov')).toBe(false);
	});

	test('no quorum and no optional members behaves exactly as before', () => {
		const plain = computeAvailability(input());
		const explicit = computeAvailability(input({ requirement: { ...weekend, quorum: null, optionalMemberIds: [] } }));
		expect(explicit).toEqual(plain);
	});

	test('durations: stretches long enough where enough people are free', () => {
		const evening: Requirement = {
			...weekend,
			length: { type: 'duration', minutes: 120 },
			window: { from: '18:00', to: '23:59' },
			days: [],
			quorum: 2
		};
		const twoBusy: MemberBusy[] = [
			{ userId: 'a', synced: true, busy: [busy('2026-11-10T18:00:00Z', '2026-11-10T20:00:00Z')] },
			{ userId: 'b', synced: true, busy: [busy('2026-11-10T19:00:00Z', '2026-11-10T21:00:00Z')] },
			{ userId: 'c', synced: true, busy: [] }
		];
		const day = computeAvailability(input({ requirement: evening, members: twoBusy, from: '2026-11-10', to: '2026-11-10' }));
		// 19:00–20:00 has a and b busy (only c free), so the 2-of-3 stretches are 18:00–19:00 (too short) and 20:00–midnight.
		expect(day.slots.map((s) => [s.start, s.end])).toEqual([['2026-11-10T20:00:00.000Z', '2026-11-11T00:00:00.000Z']]);
		const detail = computeDay(input({ requirement: evening, members: twoBusy }), '2026-11-10');
		expect(detail.mostFree?.map((i) => i.start)).toEqual(['2026-11-10T18:00:00.000Z', '2026-11-10T20:00:00.000Z']);
	});
});
