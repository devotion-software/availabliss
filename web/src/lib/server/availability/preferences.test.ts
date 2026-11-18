import { describe, expect, test } from 'bun:test';
import type { Availability } from '#lib/types';
import { applyPreferences, fits, preferenceLabel } from './preferences';

const TZ = 'Europe/London';
const at = (iso: string) => Date.parse(iso);
const weekendsAfter4 = {
	userId: 'sam',
	kind: 'weekly' as const,
	from: null,
	to: null,
	days: [5, 6] as (5 | 6)[],
	window: { from: '16:00', to: '23:59' }
};
const christmas = { userId: 'priya', kind: 'dates' as const, from: '2026-12-19', to: '2026-12-21', days: [], window: null };

describe('preferences', () => {
	test('weekly: the day and the window must both fit', () => {
		// Sat 21 Nov 2026, 18:00–21:00 London (GMT).
		expect(fits(weekendsAfter4, at('2026-11-21T18:00:00Z'), at('2026-11-21T21:00:00Z'), TZ)).toBe(true);
		// Same Saturday, morning only.
		expect(fits(weekendsAfter4, at('2026-11-21T09:00:00Z'), at('2026-11-21T12:00:00Z'), TZ)).toBe(false);
		// A Friday evening.
		expect(fits(weekendsAfter4, at('2026-11-20T18:00:00Z'), at('2026-11-20T21:00:00Z'), TZ)).toBe(false);
		// A Fri 18:00 → Sun weekend touches Saturday afternoon.
		expect(fits(weekendsAfter4, at('2026-11-20T18:00:00Z'), at('2026-11-23T00:00:00Z'), TZ)).toBe(true);
	});

	test('dates: any overlap with the range', () => {
		expect(fits(christmas, at('2026-12-21T10:00:00Z'), at('2026-12-21T12:00:00Z'), TZ)).toBe(true);
		expect(fits(christmas, at('2026-12-22T10:00:00Z'), at('2026-12-22T12:00:00Z'), TZ)).toBe(false);
	});

	test('marks slots and match days, never changes what works', () => {
		const availability: Availability = {
			from: '2026-11-20',
			to: '2026-11-22',
			visibleWeekdays: [4, 5, 6],
			days: [
				{ date: '2026-11-20', status: 'match', busyMemberIds: [], slotPosition: 'start', holiday: null },
				{ date: '2026-11-21', status: 'match', busyMemberIds: [], slotPosition: 'middle', holiday: null },
				{ date: '2026-11-22', status: 'busy', busyMemberIds: ['tom'], slotPosition: null, holiday: null }
			],
			slots: [{ start: '2026-11-20T18:00:00.000Z', end: '2026-11-22T00:00:00.000Z', label: 'x' }],
			syncedCount: 3,
			memberCount: 3,
			pendingMemberIds: [],
			checkedUntil: []
		};
		const out = applyPreferences(availability, [weekendsAfter4, christmas], TZ);
		expect(out.slots[0].preferredBy).toEqual(['sam']);
		expect(out.days.map((d) => d.preferredBy)).toEqual([[], ['sam'], undefined]);
		expect(out.days.map((d) => d.status)).toEqual(['match', 'match', 'busy']);
	});

	test('labels', () => {
		expect(preferenceLabel(weekendsAfter4, TZ)).toBe('Weekends after 16:00');
		expect(preferenceLabel({ ...weekendsAfter4, days: [0, 1, 2, 3, 4], window: { from: '12:00', to: '14:00' } }, TZ)).toBe('Weekdays 12:00–14:00');
		expect(preferenceLabel({ ...weekendsAfter4, days: [], window: null }, TZ)).toBe('Any day');
		expect(preferenceLabel(christmas, TZ)).toBe('19 – 21 Dec');
		expect(preferenceLabel({ ...christmas, to: '2027-01-02' }, TZ)).toBe('19 Dec – 2 Jan');
	});
});
