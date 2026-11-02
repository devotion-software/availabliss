import { expect, test } from 'bun:test';
import { applyPreset, defaultRequirement, presetOf } from './requirements';

test('a week opens up every day, shorter presets keep the chosen days', () => {
	const week = defaultRequirement('Europe/London', '2026-11-02', 'week');
	expect(week.days).toEqual([0, 1, 2, 3, 4, 5, 6]);
	expect(presetOf(week)).toBe('week');

	const weekend = defaultRequirement('Europe/London', '2026-11-02', 'weekend');
	expect(weekend.days).toEqual([4, 5, 6]);
	expect(applyPreset(weekend, 'evening').days).toEqual([4, 5, 6]);
	expect(applyPreset(weekend, 'week').days).toEqual([0, 1, 2, 3, 4, 5, 6]);
});
