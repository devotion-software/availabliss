import { expect, test } from 'bun:test';
import { defaultRequirement, today } from './requirements';
import { requirementSchema } from '#lib/schemas';

test('today is the calendar date in the given zone', () => {
	const instant = Date.parse('2026-11-02T23:30:00Z');
	expect(today('Europe/London', instant)).toBe('2026-11-02');
	expect(today('Asia/Tokyo', instant)).toBe('2026-11-03');
	expect(today('America/Los_Angeles', instant)).toBe('2026-11-02');
});

test('defaultRequirement is a valid weekend search over the next 13 weeks', () => {
	const req = defaultRequirement('Europe/Paris');
	expect(requirementSchema.parse(req)).toEqual(req);
	expect(req.length).toEqual({ type: 'span', startDay: 4, startTime: '18:00', endDay: 6, endTime: '23:59', weeksLater: 0 });
	expect(req.timezone).toBe('Europe/Paris');
	expect(req.range.from).toBe(today('Europe/Paris'));
	const days = (Date.parse(req.range.to) - Date.parse(req.range.from)) / 86_400_000;
	expect(days).toBe(91);
});
