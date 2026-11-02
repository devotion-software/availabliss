import { expect, test } from 'bun:test';
import { PEOPLE, grid, slots, weekdayOf } from './demo';

test('calendar facts', () => {
	expect(weekdayOf(1)).toBe(6); // Sunday 1 November 2026
	expect(weekdayOf(6)).toBe(4); // Friday
});

test('weekends that work for everyone, and who unblocks more', () => {
	expect(slots('weekend', PEOPLE).map((s) => s.label)).toEqual(['Fri 27 – Sun 29 Nov']);
	const withoutAna = PEOPLE.filter((p) => p.id !== 'ana');
	expect(slots('weekend', withoutAna).map((s) => s.label)).toEqual(['Fri 13 – Sun 15 Nov', 'Fri 27 – Sun 29 Nov']);
});

test('evenings are single days on Tue–Thu', () => {
	const s = slots('evening', PEOPLE);
	expect(s.every((x) => x.days.length === 1)).toBe(true);
	expect(s.map((x) => x.days[0])).not.toContain(10); // Sam's busy Tue 10
});

test('grid marks matches as joined bands and names who is busy', () => {
	const cells = grid('weekend', PEOPLE);
	expect(cells.length % 3).toBe(0);
	const by = (d: number) => cells.find((c) => c.day === d)!;
	expect([by(27).band, by(28).band, by(29).band]).toEqual(['start', 'middle', 'end']);
	expect(by(14)).toMatchObject({ kind: 'busy', busyNames: ['Ana'] });
	expect(by(13).kind).toBe('free');
});
