import { afterEach, beforeEach, describe, expect, spyOn, test } from 'bun:test';
import { caldav, parseBusyEvents, xmlBlocks, xmlText } from './caldav';
import { resolver } from './public-fetch';
import { ReauthRequired } from './types';

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

describe('parseBusyEvents edge cases', () => {
	test('floating times use the user zone; unknown TZIDs fall back to it', () => {
		const [floating] = parseBusyEvents(ics('DTSTART:20260701T100000\r\nDTEND:20260701T110000'), 'Europe/London');
		expect(new Date(floating.start).toISOString()).toBe('2026-07-01T09:00:00.000Z');
		const [bogus] = parseBusyEvents(ics('DTSTART;TZID=Not/AZone:20260701T100000\r\nDTEND;TZID=Not/AZone:20260701T110000'), 'Europe/London');
		expect(bogus).toEqual(floating);
	});

	test('keeps seconds on zoned times', () => {
		const [span] = parseBusyEvents(ics('DTSTART;TZID=UTC:20260701T100030\r\nDTEND:20260701T110000Z'), 'UTC');
		expect(new Date(span.start).toISOString()).toBe('2026-07-01T10:00:30.000Z');
	});

	test('durations in weeks, days, hours, minutes and seconds', () => {
		const len = (d: string) => {
			const [s] = parseBusyEvents(ics(`DTSTART:20261113T180000Z\r\nDURATION:${d}`), 'UTC');
			return s ? s.end - s.start : null;
		};
		expect(len('P1W')).toBe(7 * 86_400_000);
		expect(len('P1DT2H')).toBe(26 * 3_600_000);
		expect(len('PT45M30S')).toBe((45 * 60 + 30) * 1000);
		expect(len('garbage')).toBeNull();
	});

	test('all-day without an end lasts one day; timed without an end is dropped', () => {
		const [day] = parseBusyEvents(ics('DTSTART;VALUE=DATE:20261113'), 'UTC');
		expect(day.end - day.start).toBe(86_400_000);
		expect(parseBusyEvents(ics('DTSTART:20261113T180000Z'), 'UTC')).toEqual([]);
	});

	test('skips unparseable or inverted events and reads several', () => {
		const doc = [
			'BEGIN:VCALENDAR',
			'BEGIN:VEVENT\r\nDTSTART:not-a-date\r\nDTEND:20261113T200000Z\r\nEND:VEVENT',
			'BEGIN:VEVENT\r\nDTSTART:20261113T200000Z\r\nDTEND:20261113T180000Z\r\nEND:VEVENT',
			'BEGIN:VEVENT\r\nDTSTART:20261114T100000Z\r\nDTEND:20261114T110000Z\r\nEND:VEVENT',
			'BEGIN:VEVENT\r\nSUMMARY:no start\r\nEND:VEVENT',
			'BEGIN:VEVENT\r\nDTSTART:20261115T100000Z\r\nDTEND:20261115T110000Z\r\nEND:VEVENT',
			'END:VCALENDAR'
		].join('\r\n');
		expect(parseBusyEvents(doc, 'UTC').map((s) => new Date(s.start).toISOString())).toEqual([
			'2026-11-14T10:00:00.000Z',
			'2026-11-15T10:00:00.000Z'
		]);
	});
});

describe('xmlText', () => {
	test('CDATA, numeric entities and missing tags', () => {
		expect(xmlText('<cal:calendar-data><![CDATA[A & B]]></cal:calendar-data>', 'calendar-data')).toBe('A & B');
		expect(xmlText('<d:x>&#65;&#x42;&lt;&gt;&quot;&apos;&amp;amp;</d:x>', 'x')).toBe(`AB<>"'&amp;`);
		expect(xmlText('<d:x/>', 'y')).toBeNull();
	});
});

describe('caldav adapter (stubbed fetch)', () => {
	const realFetch = globalThis.fetch;
	let dns: ReturnType<typeof spyOn>;
	beforeEach(() => {
		dns = spyOn(resolver, 'lookup').mockImplementation(async (host: string) => [host === 'internal.example.com' ? '10.0.0.5' : '93.184.215.14']);
	});
	afterEach(() => {
		globalThis.fetch = realFetch;
		dns.mockRestore();
	});

	type Call = { url: string; method: string; depth: string | null; auth: string | null; body: string };
	function stub(routes: (call: Call) => { status?: number; body: string; url?: string }) {
		const calls: Call[] = [];
		globalThis.fetch = (async (input: URL | string, init?: RequestInit) => {
			const headers = new Headers(init?.headers);
			const call = { url: String(input), method: init?.method ?? 'GET', depth: headers.get('depth'), auth: headers.get('authorization'), body: String(init?.body ?? '') };
			calls.push(call);
			const r = routes(call);
			const res = new Response(r.body, { status: r.status ?? 207 });
			Object.defineProperty(res, 'url', { value: r.url ?? call.url });
			return res;
		}) as typeof fetch;
		return calls;
	}

	const creds = { serverUrl: 'https://dav.example.com', username: 'sam@example.com', password: 'app-pass' };

	test('discovers the calendar home and lists event calendars', async () => {
		const calls = stub((c) => {
			if (c.body.includes('current-user-principal'))
				return { body: '<d:multistatus><d:response><d:propstat><d:prop><d:current-user-principal><d:href>/123/principal/</d:href></d:current-user-principal></d:prop></d:propstat></d:response></d:multistatus>' };
			if (c.body.includes('calendar-home-set'))
				return { body: '<multistatus><response><propstat><prop><C:calendar-home-set><href>/123/calendars/</href></C:calendar-home-set></prop></propstat></response></multistatus>' };
			return {
				body: `<d:multistatus>
<d:response><d:href>/123/calendars/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
<d:response><d:href>/123/calendars/home/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/><c:calendar/></d:resourcetype><d:displayname>Home &amp; Away</d:displayname><x:calendar-color>#FF2968FF</x:calendar-color><c:supported-calendar-component-set><c:comp name="VEVENT"/></c:supported-calendar-component-set></d:prop></d:propstat></d:response>
<d:response><d:href>/123/calendars/tasks/</d:href><d:propstat><d:prop><d:resourcetype><c:calendar/></d:resourcetype><d:displayname>Tasks</d:displayname><c:supported-calendar-component-set><c:comp name="VTODO"/></c:supported-calendar-component-set></d:prop></d:propstat></d:response>
<d:response><d:href>/123/calendars/plain/</d:href><d:propstat><d:prop><d:resourcetype><c:calendar/></d:resourcetype><d:displayname></d:displayname></d:prop></d:propstat></d:response>
</d:multistatus>`
			};
		});
		expect(await caldav.listCalendars(creds)).toEqual([
			{ externalId: 'https://dav.example.com/123/calendars/home/', name: 'Home & Away', color: '#FF2968' },
			{ externalId: 'https://dav.example.com/123/calendars/plain/', name: 'Calendar', color: null }
		]);
		expect(calls.map((c) => [c.method, c.url, c.depth])).toEqual([
			['PROPFIND', 'https://dav.example.com/', '0'],
			['PROPFIND', 'https://dav.example.com/123/principal/', '0'],
			['PROPFIND', 'https://dav.example.com/123/calendars/', '1']
		]);
		expect(calls[0].auth).toBe(`Basic ${Buffer.from('sam@example.com:app-pass').toString('base64')}`);
	});

	test('rejected credentials ask the user to reconnect', async () => {
		stub(() => ({ status: 401, body: '' }));
		await expect(caldav.listCalendars(creds)).rejects.toBeInstanceOf(ReauthRequired);
		stub(() => ({ status: 403, body: '' }));
		await expect(caldav.listCalendars(creds)).rejects.toBeInstanceOf(ReauthRequired);
	});

	test('server errors and missing principals are plain errors', async () => {
		stub(() => ({ status: 500, body: '' }));
		await expect(caldav.listCalendars(creds)).rejects.toThrow('500');
		stub(() => ({ body: '<d:multistatus/>' }));
		await expect(caldav.listCalendars(creds)).rejects.toThrow('principal');
	});

	test('busy runs a time-range REPORT per calendar and parses the data', async () => {
		const event = 'BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:20261113T180000Z\r\nDTEND:20261113T200000Z\r\nEND:VEVENT\r\nEND:VCALENDAR';
		const calls = stub(() => ({ body: `<d:multistatus><d:response><c:calendar-data>${event.replace(/&/g, '&amp;')}</c:calendar-data></d:response></d:multistatus>` }));
		const cal = { externalId: 'https://dav.example.com/123/calendars/home/', name: 'Home', color: null };
		const busy = await caldav.busy(creds, [cal], new Date('2026-11-01T00:00:00Z'), new Date('2026-12-01T00:00:00Z'), 'UTC');
		expect(busy).toEqual([{ calendarExternalId: cal.externalId, start: new Date('2026-11-13T18:00:00Z'), end: new Date('2026-11-13T20:00:00Z') }]);
		expect(calls[0].method).toBe('REPORT');
		expect(calls[0].body).toContain('start="20261101T000000Z" end="20261201T000000Z"');
	});

	test('refuses discovery hrefs that point at internal hosts', async () => {
		const calls = stub((c) => {
			if (c.body.includes('current-user-principal'))
				return { body: '<multistatus><response><propstat><prop><current-user-principal><href>https://169.254.169.254/latest/</href></current-user-principal></prop></propstat></response></multistatus>' };
			return { body: '' };
		});
		await expect(caldav.listCalendars(creds)).rejects.toThrow("isn't allowed");
		expect(calls.length).toBe(1);
	});

	test('re-checks redirects and drops credentials across origins', async () => {
		const calls = stub((c) => {
			if (c.url === 'https://dav.example.com/') return { status: 302, body: '', url: c.url };
			return { body: '' };
		});
		const realStub = globalThis.fetch;
		globalThis.fetch = (async (input: URL | string, init?: RequestInit) => {
			const res = await realStub(input, init);
			if (res.status === 302) return new Response(null, { status: 302, headers: { location: 'https://internal.example.com/' } });
			return res;
		}) as typeof fetch;
		await expect(caldav.listCalendars(creds)).rejects.toThrow("isn't allowed");
		expect(calls.map((c) => c.url)).toEqual(['https://dav.example.com/']);
	});
});
