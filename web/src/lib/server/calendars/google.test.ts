import { afterEach, describe, expect, test } from 'bun:test';
import { eventKey, eventSpan, google } from './google';
import type { RemoteCalendar } from './types';

describe('eventSpan', () => {
	test('timed events', () => {
		expect(eventSpan({ id: 'a', start: { dateTime: '2029-03-01T09:00:00Z' }, end: { dateTime: '2029-03-01T10:00:00Z' } }, 'UTC')).toEqual({
			start: Date.parse('2029-03-01T09:00:00Z'),
			end: Date.parse('2029-03-01T10:00:00Z')
		});
	});

	test('all-day events cover the local day in the calendar zone', () => {
		const span = eventSpan({ id: 'a', start: { date: '2029-07-01' }, end: { date: '2029-07-02' } }, 'Europe/London')!;
		expect(new Date(span.start).toISOString()).toBe('2029-06-30T23:00:00.000Z');
		expect(new Date(span.end).toISOString()).toBe('2029-07-01T23:00:00.000Z');
	});

	test('free, cancelled and declined events are not busy', () => {
		const times = { start: { dateTime: '2029-03-01T09:00:00Z' }, end: { dateTime: '2029-03-01T10:00:00Z' } };
		expect(eventSpan({ id: 'a', ...times, transparency: 'transparent' }, 'UTC')).toBeNull();
		expect(eventSpan({ id: 'a', ...times, status: 'cancelled' }, 'UTC')).toBeNull();
		expect(eventSpan({ id: 'a', ...times, attendees: [{ responseStatus: 'accepted' }, { self: true, responseStatus: 'declined' }] }, 'UTC')).toBeNull();
		expect(eventSpan({ id: 'a', ...times, attendees: [{ self: true, responseStatus: 'tentative' }] }, 'UTC')).not.toBeNull();
	});
});

describe('google adapter (stubbed fetch)', () => {
	const realFetch = globalThis.fetch;
	afterEach(() => {
		globalThis.fetch = realFetch;
	});

	type Call = { url: URL; method: string; body: string };
	function stub(route: (call: Call) => { status?: number; body: unknown }) {
		const calls: Call[] = [];
		globalThis.fetch = (async (input: URL | string, init?: RequestInit) => {
			const call = { url: new URL(String(input)), method: init?.method ?? 'GET', body: String(init?.body ?? '') };
			calls.push(call);
			const r = route(call);
			return new Response(JSON.stringify(r.body), { status: r.status ?? 200 });
		}) as typeof fetch;
		return calls;
	}

	const creds = { accessToken: 't', refreshToken: 'r', expiresAt: Date.now() + 3600_000 };
	const home: RemoteCalendar = { externalId: 'me@example.com', name: 'Home', color: null, changes: 'cursor' };
	const from = new Date('2026-11-01T00:00:00Z');
	const to = new Date('2029-12-01T00:00:00Z');
	const at = (iso: string) => ({ dateTime: iso });

	test('lists free/busy-only calendars as untracked', async () => {
		stub(() => ({
			body: {
				items: [
					{ id: 'me@example.com', summary: 'Me', accessRole: 'owner' },
					{ id: 'boss@example.com', summary: 'Boss', accessRole: 'freeBusyReader' }
				]
			}
		}));
		expect((await google.listCalendars(creds)).map((c) => c.changes)).toEqual(['cursor', 'none']);
	});

	test('busy lists occurrences for readable calendars and uses freeBusy for the rest', async () => {
		const calls = stub((c) =>
			c.url.pathname.endsWith('/freeBusy')
				? { body: { calendars: { 'boss@example.com': { busy: [{ start: '2026-11-02T09:00:00Z', end: '2026-11-02T10:00:00Z' }] } } } }
				: { body: { timeZone: 'UTC', items: [{ id: 'standup_20261103', recurringEventId: 'standup', start: at('2026-11-03T09:00:00Z'), end: at('2026-11-03T09:15:00Z') }] } }
		);
		const boss: RemoteCalendar = { externalId: 'boss@example.com', name: 'Boss', color: null, changes: 'none' };
		const busy = await google.busy(creds, [home, boss], from, new Date('2026-12-01T00:00:00Z'), 'UTC');
		expect(busy).toEqual([
			{ calendarExternalId: 'boss@example.com', start: new Date('2026-11-02T09:00:00Z'), end: new Date('2026-11-02T10:00:00Z') },
			// Keyed by the series, so a change to it replaces every occurrence.
			{ calendarExternalId: 'me@example.com', eventKey: eventKey('standup'), start: new Date('2026-11-03T09:00:00Z'), end: new Date('2026-11-03T09:15:00Z') }
		]);
		const list = calls.find((c) => c.url.pathname.endsWith('/events'))!;
		expect(list.url.searchParams.get('singleEvents')).toBe('true');
		expect(list.url.searchParams.get('timeMin')).toBe(from.toISOString());
	});

	test('changes: single events directly, recurring series by re-reading their occurrences', async () => {
		const calls = stub((c) => {
			if (c.url.pathname.endsWith('/events/weekly/instances'))
				return { body: { items: [{ id: 'weekly_1', recurringEventId: 'weekly', start: at('2028-01-05T18:00:00Z'), end: at('2028-01-05T19:00:00Z') }] } };
			if (c.url.pathname.endsWith('/events/gone-series/instances')) return { status: 410, body: {} };
			return {
				body: {
					timeZone: 'UTC',
					items: [
						{ id: 'dentist', start: at('2028-06-01T10:00:00Z'), end: at('2028-06-01T11:00:00Z') },
						{ id: 'cancelled-lunch', status: 'cancelled' },
						// A moved occurrence: the whole series is re-read.
						{ id: 'weekly_1', recurringEventId: 'weekly', start: at('2028-01-05T18:00:00Z'), end: at('2028-01-05T19:00:00Z') },
						{ id: 'gone-series', recurrence: ['RRULE:FREQ=DAILY'], status: 'cancelled' }
					]
				}
			};
		});
		const since = new Date('2026-11-03T08:00:00Z');
		const changes = (await google.changes!(creds, home, since, from, to, 'UTC'))!;

		const list = calls[0].url;
		expect(list.searchParams.get('updatedMin')).toBe(since.toISOString());
		expect(list.searchParams.get('showDeleted')).toBe('true');
		expect(list.searchParams.get('timeMin')).toBeNull();

		expect([...changes.keys()].sort()).toEqual([eventKey('cancelled-lunch'), eventKey('dentist'), eventKey('gone-series'), eventKey('weekly')].sort());
		expect(changes.get(eventKey('dentist'))).toEqual([
			{ calendarExternalId: home.externalId, eventKey: eventKey('dentist'), start: new Date('2028-06-01T10:00:00Z'), end: new Date('2028-06-01T11:00:00Z') }
		]);
		expect(changes.get(eventKey('cancelled-lunch'))).toEqual([]);
		expect(changes.get(eventKey('gone-series'))).toEqual([]);
		expect(changes.get(eventKey('weekly'))).toHaveLength(1);

		const instances = calls.find((c) => c.url.pathname.endsWith('/weekly/instances'))!.url;
		expect(instances.searchParams.get('timeMax')).toBe(to.toISOString());
	});

	test('changes follows pages', async () => {
		stub((c) =>
			c.url.searchParams.get('pageToken')
				? { body: { items: [{ id: 'b', start: at('2027-01-01T10:00:00Z'), end: at('2027-01-01T11:00:00Z') }] } }
				: { body: { nextPageToken: 'p2', items: [{ id: 'a', start: at('2027-01-01T08:00:00Z'), end: at('2027-01-01T09:00:00Z') }] } }
		);
		const changes = (await google.changes!(creds, home, new Date(), from, to, 'UTC'))!;
		expect(changes.size).toBe(2);
	});

	test('a cursor Google no longer accepts means a full read', async () => {
		stub(() => ({ status: 410, body: { error: { message: 'Gone' } } }));
		expect(await google.changes!(creds, home, new Date('2020-01-01T00:00:00Z'), from, to, 'UTC')).toBeNull();
	});
});

describe('google change notifications (stubbed fetch)', () => {
	const realFetch = globalThis.fetch;
	afterEach(() => {
		globalThis.fetch = realFetch;
	});
	const creds = { accessToken: 't', refreshToken: 'r', expiresAt: Date.now() + 3600_000 };
	const cal: RemoteCalendar = { externalId: 'me@example.com', name: 'Home', color: null, changes: 'cursor' };

	test('watch opens a channel with our id, address and token', async () => {
		let sent: { url: string; body: Record<string, unknown> } | null = null;
		globalThis.fetch = (async (input: URL | string, init?: RequestInit) => {
			sent = { url: String(input), body: JSON.parse(String(init?.body)) };
			return new Response(JSON.stringify({ id: 'chan-1', resourceId: 'res-1', expiration: String(Date.parse('2026-11-10T00:00:00Z')) }));
		}) as typeof fetch;
		const channel = await google.watch!(creds, cal, { id: 'chan-1', address: 'https://app.example/api/v1/calendars/push/google', token: 'secret' });
		expect(channel).toEqual({ id: 'chan-1', resourceId: 'res-1', expiresAt: new Date('2026-11-10T00:00:00Z') });
		expect(sent!.url).toBe('https://www.googleapis.com/calendar/v3/calendars/me%40example.com/events/watch');
		expect(sent!.body).toMatchObject({ id: 'chan-1', type: 'web_hook', address: 'https://app.example/api/v1/calendars/push/google', token: 'secret' });
	});

	test("calendars that can't be watched answer null", async () => {
		globalThis.fetch = (async () => new Response('{}', { status: 403 })) as unknown as typeof fetch;
		expect(await google.watch!(creds, cal, { id: 'c', address: 'https://a', token: 't' })).toBeNull();
		expect(await google.watch!(creds, { ...cal, changes: 'none' }, { id: 'c', address: 'https://a', token: 't' })).toBeNull();
	});

	test('unwatch stops the channel and ignores one already gone', async () => {
		const bodies: unknown[] = [];
		globalThis.fetch = (async (_input: URL | string, init?: RequestInit) => {
			bodies.push(JSON.parse(String(init?.body)));
			return new Response(null, { status: bodies.length === 1 ? 204 : 404 });
		}) as typeof fetch;
		await google.unwatch!(creds, { id: 'chan-1', resourceId: 'res-1' });
		await google.unwatch!(creds, { id: 'chan-1', resourceId: 'res-1' });
		expect(bodies[0]).toEqual({ id: 'chan-1', resourceId: 'res-1' });
	});
});
