import { describe, expect, test } from 'bun:test';
import type { Member, Slot } from '#lib/types';
import { fairLine, missingLine, rankSlots } from './best';

const slot = (start: string, extra: Partial<Slot> = {}): Slot => ({ start, end: start, label: start, ...extra });
const members = new Map([
	['a', { userId: 'a', name: 'Ana Silva' }],
	['b', { userId: 'b', name: 'Tom Lee' }]
] as [string, Member][]);

describe('rankSlots', () => {
	test('fewer missing, then score, then preferences, then date', () => {
		const ranked = rankSlots([
			slot('2026-12-05', { missingMemberIds: ['a'], score: 9 }),
			slot('2026-12-04', { missingMemberIds: [], score: 1 }),
			slot('2026-12-03', { missingMemberIds: [], score: 5 }),
			slot('2026-12-02', { missingMemberIds: [], score: 1, preferredBy: ['a'] }),
			slot('2026-12-01', { missingMemberIds: [], score: 1 })
		]);
		expect(ranked.map((s) => s.start)).toEqual(['2026-12-03', '2026-12-02', '2026-12-01', '2026-12-04', '2026-12-05']);
	});
	test('without scores, by preferences then date', () => {
		const ranked = rankSlots([slot('2026-12-02'), slot('2026-12-03', { preferredBy: ['a'] }), slot('2026-12-01')]);
		expect(ranked.map((s) => s.start)).toEqual(['2026-12-03', '2026-12-01', '2026-12-02']);
	});
});

describe('lines', () => {
	test('missing', () => {
		expect(missingLine([], members)).toBeNull();
		expect(missingLine(['a'], members)).toBe("Ana can't make it");
		expect(missingLine(['a', 'b'], members)).toBe("Ana and Tom can't make it");
		expect(missingLine(['a', 'b', 'c'], members)).toBe("3 people can't make it");
	});
	test('fair', () => {
		expect(fairLine(undefined, members)).toBeNull();
		expect(fairLine(['a'], members)).toBe('Fair for Ana, who missed out last time');
	});
});
