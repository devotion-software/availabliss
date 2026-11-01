import { describe, expect, test } from 'bun:test';
import { parseBusyEvents, xmlBlocks, xmlText } from './caldav';

const ics = (event: string) => `BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\n${event}\r\nEND:VEVENT\r\nEND:VCALENDAR`;

describe('parseBusyEvents', () => {
	test('reads UTC start/end', () => {
		expect(parseBusyEvents(ics('DTSTART:20261113T180000Z\r\nDTEND:20261113T200000Z'), 'Europe/London')).toEqual([
			{ start: Date.parse('2026-11-13T18:00:00Z'), end: Date.parse('2026-11-13T20:00:00Z') }
		]);
	});

	test('reads TZID times in that zone', () => {
		const [span] = parseBusyEvents(ics('DTSTART;TZID=Europe/Paris:20260701T100000\r\nDURATION:PT1H30M'), 'Europe/London');
		expect(new Date(span.start).toISOString()).toBe('2026-07-01T08:00:00.000Z');
		expect(span.end - span.start).toBe(90 * 60 * 1000);
	});

	test('treats all-day events as the whole local day', () => {
		const [span] = parseBusyEvents(ics('DTSTART;VALUE=DATE:20260701\r\nDTEND;VALUE=DATE:20260702'), 'Europe/London');
		expect(new Date(span.start).toISOString()).toBe('2026-06-30T23:00:00.000Z');
		expect(new Date(span.end).toISOString()).toBe('2026-07-01T23:00:00.000Z');
	});

	test('skips free (transparent) and cancelled events', () => {
		expect(parseBusyEvents(ics('DTSTART:20261113T180000Z\r\nDTEND:20261113T200000Z\r\nTRANSP:TRANSPARENT'), 'UTC')).toEqual([]);
		expect(parseBusyEvents(ics('DTSTART:20261113T180000Z\r\nDTEND:20261113T200000Z\r\nSTATUS:CANCELLED'), 'UTC')).toEqual([]);
	});

	test('unfolds continuation lines', () => {
		expect(parseBusyEvents(ics('DTSTART:20261113T18\r\n 0000Z\r\nDTEND:20261113T200000Z'), 'UTC')).toHaveLength(1);
	});
});

describe('xml helpers', () => {
	const xml = `<D:multistatus xmlns:D="DAV:"><D:response><D:href>/cal/one/</D:href><D:propstat><D:prop><D:displayname>Home &amp; Family</D:displayname></D:prop></D:propstat></D:response><response><href>/cal/two/</href></response></D:multistatus>`;

	test('finds blocks regardless of namespace prefix', () => {
		expect(xmlBlocks(xml, 'response')).toHaveLength(2);
	});

	test('decodes text', () => {
		expect(xmlText(xml, 'displayname')).toBe('Home & Family');
	});
});
