import { describe, expect, test } from 'bun:test';
import type { GroupEvent } from '#lib/types';
import { buildIcs } from './ics';

const event = (over: Partial<GroupEvent> = {}): GroupEvent => ({
	id: 'evt-1',
	groupId: 'g1',
	proposalId: null,
	title: 'Dinner',
	location: null,
	note: null,
	start: '2026-11-13T18:00:00.000Z',
	end: '2026-11-13T21:30:00.000Z',
	allDay: false,
	createdBy: 'u1',
	createdAt: '2026-11-01T00:00:00.000Z',
	...over
});

const lines = (ics: string) => ics.split('\r\n');

describe('buildIcs', () => {
	test('timed event uses UTC stamps and CRLF line endings', () => {
		const ics = buildIcs(event(), 'Climbers', 'availabliss.app', 'Europe/London');
		expect(ics.endsWith('END:VCALENDAR\r\n')).toBe(true);
		expect(ics).not.toMatch(/[^\r]\n/);
		const l = lines(ics);
		expect(l[0]).toBe('BEGIN:VCALENDAR');
		expect(l).toContain('UID:evt-1@availabliss.app');
		expect(l).toContain('DTSTART:20261113T180000Z');
		expect(l).toContain('DTEND:20261113T213000Z');
		expect(l).toContain('SUMMARY:Dinner');
		expect(l.find((x) => x.startsWith('DTSTAMP:'))).toMatch(/^DTSTAMP:\d{8}T\d{6}Z$/);
		expect(l.some((x) => x.startsWith('LOCATION'))).toBe(false);
		expect(l).toContain('DESCRIPTION:Planned with Climbers on Availabliss');
	});

	test('all-day events are dates in the group timezone', () => {
		// Midnight BST on 1 July is 23:00 UTC on 30 June.
		const ics = buildIcs(
			event({ allDay: true, start: '2026-06-30T23:00:00.000Z', end: '2026-07-02T23:00:00.000Z' }),
			'G',
			'h',
			'Europe/London'
		);
		expect(lines(ics)).toContain('DTSTART;VALUE=DATE:20260701');
		expect(lines(ics)).toContain('DTEND;VALUE=DATE:20260703');
	});

	test('a carriage return in text cannot start a new property', () => {
		const ics = buildIcs(event({ title: 'Hi\rATTENDEE:mailto:x@example.com', note: 'a\r\nb' }), 'G', 'h', 'UTC');
		expect(lines(ics)).toContain('SUMMARY:Hi\\nATTENDEE:mailto:x@example.com');
		expect(lines(ics).some((l) => l.startsWith('ATTENDEE'))).toBe(false);
		expect(ics).not.toMatch(/\r(?!\n)/);
		expect(lines(ics)).toContain('DESCRIPTION:a\\nb\\n\\nPlanned with G on Availabliss');
	});

	test('escapes text values and joins note with the footer', () => {
		const ics = buildIcs(event({ title: 'A, b; c\\d', location: 'Pub; upstairs', note: 'Bring\ncash' }), 'G', 'h', 'UTC');
		expect(lines(ics)).toContain('SUMMARY:A\\, b\\; c\\\\d');
		expect(lines(ics)).toContain('LOCATION:Pub\\; upstairs');
		expect(lines(ics)).toContain('DESCRIPTION:Bring\\ncash\\n\\nPlanned with G on Availabliss');
	});

	test('folds lines longer than 75 octets, counting multi-byte characters', () => {
		const ics = buildIcs(event({ title: 'é'.repeat(100) }), 'G', 'h', 'UTC');
		const l = lines(ics);
		for (const line of l) expect(Buffer.byteLength(line)).toBeLessThanOrEqual(75);
		const start = l.findIndex((x) => x.startsWith('SUMMARY:'));
		expect(l[start + 1].startsWith(' ')).toBe(true);
		// Unfolding restores the original value.
		const unfolded = ics.replace(/\r\n /g, '');
		expect(unfolded).toContain(`SUMMARY:${'é'.repeat(100)}\r\n`);
	});
});
