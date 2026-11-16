import { expect, test } from 'bun:test';
import type { Requirement } from '#lib/types';
import { defaultRequirement } from './requirements';
import { matchingTemplate, requirementFromTemplate, sameTiming } from './templates';

test('templates restart their search range today and drop past dates', () => {
	const saved: Requirement = {
		...defaultRequirement('Europe/London', '2026-01-10', 'weekend'),
		range: { from: '2026-01-10', to: '2026-03-10' },
		dates: [
			{ from: '2026-02-01', to: '2026-02-05' },
			{ from: '2026-12-20', to: '2026-12-31' }
		],
		customSchoolHolidays: [{ from: '2026-10-26', to: '2026-10-30' }]
	};
	const r = requirementFromTemplate({ requirement: saved }, '2026-11-02', 'America/New_York');
	expect(r.range).toEqual({ from: '2026-11-02', to: '2026-12-31' });
	expect(r.dates).toEqual([{ from: '2026-12-20', to: '2026-12-31' }]);
	expect(r.customSchoolHolidays).toEqual([]);
	expect(r.timezone).toBe('America/New_York');
	expect(r.length).toEqual(saved.length);
	expect(saved.dates).toHaveLength(2); // the template itself is untouched
});

test('a timing matches the template it was saved as, wherever its range starts', () => {
	const saved: Requirement = {
		...defaultRequirement('Europe/London', '2026-01-10', 'weekend'),
		range: { from: '2026-01-10', to: '2026-03-10' }
	};
	const template = { id: 't1', name: 'Weekends', requirement: saved, createdAt: '2026-01-10T00:00:00Z' };
	expect(matchingTemplate([template], saved)?.id).toBe('t1');
	// Used for a new group: range moves to today, zone to yours.
	expect(matchingTemplate([template], requirementFromTemplate(template, '2026-11-02', 'America/New_York'))?.id).toBe('t1');
	// Key order from the server doesn't matter.
	const reordered = Object.fromEntries(Object.entries(saved).reverse()) as unknown as Requirement;
	expect(sameTiming(reordered, saved)).toBe(true);
	// A different rule or range length is a different timing.
	expect(sameTiming({ ...saved, days: [0, 1] }, saved)).toBe(false);
	expect(sameTiming({ ...saved, range: { from: '2026-01-10', to: '2026-04-10' } }, saved)).toBe(false);
});
