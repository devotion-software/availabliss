import { expect, test } from 'bun:test';
import type { Requirement } from '#lib/types';
import { defaultRequirement } from './requirements';
import { requirementFromTemplate } from './templates';

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
