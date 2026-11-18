import { describe, expect, test } from 'bun:test';
import { scoreSlots } from './best-date';

const now = Date.parse('2032-03-01T12:00:00Z');
const slot = (day: number, preferredBy: string[] = []) => ({
	start: `2032-03-${String(day).padStart(2, '0')}T18:00:00Z`,
	end: `2032-03-${String(day).padStart(2, '0')}T21:00:00Z`,
	label: `Day ${day}`,
	preferredBy
});

describe('scoreSlots', () => {
	test('a time suiting someone who keeps compromising beats one suiting someone who never has', () => {
		const [ana, tom] = scoreSlots([slot(10, ['ana']), slot(11, ['tom'])], new Map([['ana', 2]]), now);
		expect(ana.score!).toBeGreaterThan(tom.score!);
		expect(ana.fairFor).toEqual(['ana']);
		expect(tom.fairFor).toEqual([]);
	});

	test('more people preferring it counts; tomorrow loses to next week', () => {
		const [both, one] = scoreSlots([slot(10, ['a', 'b']), slot(10, ['a'])], new Map(), now);
		expect(both.score!).toBeGreaterThan(one.score!);
		const [soon, later] = scoreSlots([slot(1), slot(8)], new Map(), now);
		expect(later.score!).toBeGreaterThan(soon.score!);
	});

	test('compromises stop adding weight after a few', () => {
		const [a] = scoreSlots([slot(10, ['x'])], new Map([['x', 4]]), now);
		const [b] = scoreSlots([slot(10, ['x'])], new Map([['x', 40]]), now);
		expect(a.score).toBe(b.score);
	});
});
