import { describe, expect, test } from 'bun:test';
import type { Repeat } from '#lib/types';
import { expandBlock, toManualBlock } from './blocks';

const block = (start: string, end: string, repeat: Repeat = 'none', until: string | null = null) => ({
	start: new Date(start),
	end: new Date(end),
	repeat,
	until
});
const at = (s: string) => Date.parse(s);
const isoSpans = (spans: { start: number; end: number }[]) =>
	spans.map((s) => [new Date(s.start).toISOString(), new Date(s.end).toISOString()]);

const TZ = 'Europe/London';

describe('expandBlock (one-off)', () => {
	const b = block('2026-11-13T18:00:00Z', '2026-11-13T20:00:00Z');

	test('included when it overlaps the range', () => {
		expect(expandBlock(b, at('2026-11-13T19:00:00Z'), at('2026-11-14T00:00:00Z'), TZ)).toEqual([
			{ start: at('2026-11-13T18:00:00Z'), end: at('2026-11-13T20:00:00Z') }
		]);
	});

	test('ranges are half-open', () => {
		expect(expandBlock(b, at('2026-11-13T20:00:00Z'), at('2026-11-14T00:00:00Z'), TZ)).toEqual([]);
		expect(expandBlock(b, at('2026-11-13T00:00:00Z'), at('2026-11-13T18:00:00Z'), TZ)).toEqual([]);
	});
});

describe('expandBlock (repeating)', () => {
	test('weekly occurrences within the range', () => {
		const b = block('2026-11-02T18:00:00Z', '2026-11-02T19:00:00Z', 'weekly');
		expect(isoSpans(expandBlock(b, at('2026-11-01T00:00:00Z'), at('2026-11-24T00:00:00Z'), TZ))).toEqual([
			['2026-11-02T18:00:00.000Z', '2026-11-02T19:00:00.000Z'],
			['2026-11-09T18:00:00.000Z', '2026-11-09T19:00:00.000Z'],
			['2026-11-16T18:00:00.000Z', '2026-11-16T19:00:00.000Z'],
			['2026-11-23T18:00:00.000Z', '2026-11-23T19:00:00.000Z']
		]);
	});

	test('nothing before the first occurrence', () => {
		const b = block('2026-11-02T18:00:00Z', '2026-11-02T19:00:00Z', 'weekly');
		expect(expandBlock(b, at('2026-10-01T00:00:00Z'), at('2026-11-02T00:00:00Z'), TZ)).toEqual([]);
	});

	test('fortnightly skips alternate weeks', () => {
		const b = block('2026-11-02T18:00:00Z', '2026-11-02T19:00:00Z', 'fortnightly');
		const starts = expandBlock(b, at('2026-11-01T00:00:00Z'), at('2026-12-31T00:00:00Z'), TZ).map((s) =>
			new Date(s.start).toISOString().slice(0, 10)
		);
		expect(starts).toEqual(['2026-11-02', '2026-11-16', '2026-11-30', '2026-12-14', '2026-12-28']);
	});

	test('until is the last date that can start an occurrence', () => {
		const b = block('2026-11-02T18:00:00Z', '2026-11-02T19:00:00Z', 'weekly', '2026-11-16');
		const starts = expandBlock(b, at('2026-11-01T00:00:00Z'), at('2027-01-01T00:00:00Z'), TZ).map((s) =>
			new Date(s.start).toISOString().slice(0, 10)
		);
		expect(starts).toEqual(['2026-11-02', '2026-11-09', '2026-11-16']);
	});

	test('a range far in the future jumps straight to it', () => {
		const b = block('2020-01-06T09:00:00Z', '2020-01-06T10:00:00Z', 'weekly');
		const spans = expandBlock(b, at('2026-11-02T00:00:00Z'), at('2026-11-09T00:00:00Z'), 'UTC');
		expect(isoSpans(spans)).toEqual([['2026-11-02T09:00:00.000Z', '2026-11-02T10:00:00.000Z']]);
	});

	test('an occurrence already in progress at the start of the range is included', () => {
		// Weekly overnight block Fri 22:00 → Sat 06:00.
		const b = block('2026-11-06T22:00:00Z', '2026-11-07T06:00:00Z', 'weekly');
		const spans = expandBlock(b, at('2026-11-14T00:00:00Z'), at('2026-11-15T00:00:00Z'), 'UTC');
		expect(isoSpans(spans)).toEqual([['2026-11-13T22:00:00.000Z', '2026-11-14T06:00:00.000Z']]);
	});

	test('keeps the local wall-clock time across DST', () => {
		// 18:00 BST on Mon 19 Oct is 17:00 UTC; after the clocks go back it is 18:00 UTC.
		const b = block('2026-10-19T17:00:00Z', '2026-10-19T18:00:00Z', 'weekly');
		expect(isoSpans(expandBlock(b, at('2026-10-18T00:00:00Z'), at('2026-11-03T00:00:00Z'), TZ))).toEqual([
			['2026-10-19T17:00:00.000Z', '2026-10-19T18:00:00.000Z'],
			['2026-10-26T18:00:00.000Z', '2026-10-26T19:00:00.000Z'],
			['2026-11-02T18:00:00.000Z', '2026-11-02T19:00:00.000Z']
		]);
	});
});

test('toManualBlock serialises dates', () => {
	const row = {
		id: 'b1',
		userId: 'u1',
		start: new Date('2026-11-13T18:00:00Z'),
		end: new Date('2026-11-13T20:00:00Z'),
		allDay: false,
		repeat: 'weekly' as const,
		until: '2026-12-31',
		note: 'Gym',
		createdAt: new Date(),
		updatedAt: new Date()
	};
	expect(toManualBlock(row as Parameters<typeof toManualBlock>[0])).toEqual({
		id: 'b1',
		start: '2026-11-13T18:00:00.000Z',
		end: '2026-11-13T20:00:00.000Z',
		allDay: false,
		repeat: 'weekly',
		until: '2026-12-31',
		note: 'Gym'
	});
});
