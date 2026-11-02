import { expect, test } from 'bun:test';
import { buildPrompt, resolvePlan, type Plan } from './planner';

const slots = [
	{ start: '2026-11-13T18:00:00Z', end: '2026-11-15T23:59:00Z', label: 'Fri 13 – Sun 15 Nov' },
	{ start: '2026-11-27T18:00:00Z', end: '2026-11-29T23:59:00Z', label: 'Fri 27 – Sun 29 Nov' },
	{ start: '2026-12-04T18:00:00Z', end: '2026-12-06T23:59:00Z', label: 'Fri 4 – Sun 6 Dec' }
];

const plan = (over: Partial<Plan> = {}): Plan => ({
	best: { slot: 2, why: 'More notice.' },
	alternatives: [
		{ slot: 2, why: 'dup of best' },
		{ slot: 9, why: 'made up' },
		{ slot: 1, why: 'Sooner.' },
		{ slot: 3, why: 'Later.' }
	],
	ideas: [{ title: 'Cottage weekend', description: 'Walks and a pub.' }],
	checklist: ['Book somewhere'],
	proposalTitle: 'Lakes weekend',
	proposalNote: 'Shall we?',
	...over
});

test('resolvePlan maps numbers to real slots and drops duplicates and inventions', () => {
	const r = resolvePlan(plan(), slots);
	expect(r.best.slot).toEqual(slots[1]);
	expect(r.alternatives.map((a) => a.slot.label)).toEqual(['Fri 13 – Sun 15 Nov', 'Fri 4 – Sun 6 Dec']);
});

test('resolvePlan rejects an unknown best slot', () => {
	expect(() => resolvePlan(plan({ best: { slot: 0, why: '' } }), slots)).toThrow();
});

test('buildPrompt numbers the slots and never needs member names', () => {
	const prompt = buildPrompt({
		groupName: 'Lake District crew',
		timing: 'A weekend · Fri from 18:00 → Sun',
		today: '2026-11-02',
		timeZone: 'Europe/London',
		members: 5,
		synced: 4,
		slots,
		holidays: [{ date: '2026-12-25', name: 'Christmas Day' }],
		notes: 'Keep it cheap'
	});
	expect(prompt).toContain('1. Fri 13 – Sun 15 Nov');
	expect(prompt).toContain('3. Fri 4 – Sun 6 Dec');
	expect(prompt).toContain('Christmas Day');
	expect(prompt).toContain('Notes from the person asking: Keep it cheap');
});
