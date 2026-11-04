import { afterEach, describe, expect, test } from 'bun:test';
import { microsoft } from './microsoft';

describe('microsoft change notifications (stubbed fetch)', () => {
	const realFetch = globalThis.fetch;
	afterEach(() => {
		globalThis.fetch = realFetch;
	});
	const creds = { accessToken: 't', refreshToken: 'r', expiresAt: Date.now() + 3600_000 };
	const stub = (route: (url: URL, init?: RequestInit) => Response) => {
		const calls: { url: URL; method: string }[] = [];
		globalThis.fetch = (async (input: URL | string, init?: RequestInit) => {
			const url = new URL(String(input));
			calls.push({ url, method: init?.method ?? 'GET' });
			return route(url, init);
		}) as typeof fetch;
		return calls;
	};

	test('watch subscribes to the calendar’s events with our token, under the 4230-minute limit', async () => {
		let body: Record<string, string> = {};
		stub((_url, init) => {
			body = JSON.parse(String(init?.body));
			return Response.json({ id: 'sub-1', expirationDateTime: '2026-11-06T10:00:00Z' });
		});
		const channel = await microsoft.watch!(creds, { externalId: 'AAMk', name: 'Calendar', color: null, changes: 'none' }, { id: 'ignored', address: 'https://app.example/api/v1/calendars/push/microsoft', token: 'secret' });
		expect(channel).toEqual({ id: 'sub-1', resourceId: null, expiresAt: new Date('2026-11-06T10:00:00Z') });
		expect(body).toMatchObject({ changeType: 'created,updated,deleted', resource: 'me/calendars/AAMk/events', clientState: 'secret' });
		expect(Date.parse(body.expirationDateTime) - Date.now()).toBeLessThan(4230 * 60 * 1000);
	});

	test('unwatch deletes the subscription, and one already gone is fine', async () => {
		let status = 204;
		const calls = stub(() => new Response(null, { status }));
		await microsoft.unwatch!(creds, { id: 'sub-1', resourceId: null });
		status = 404;
		await microsoft.unwatch!(creds, { id: 'sub-1', resourceId: null });
		expect(calls.map((c) => [c.method, c.url.pathname])).toEqual([
			['DELETE', '/v1.0/subscriptions/sub-1'],
			['DELETE', '/v1.0/subscriptions/sub-1']
		]);
	});

	test('changeIsFar: past the near tier, recurring, deleted or unknown events are far', async () => {
		const nearEnd = new Date('2027-05-01T00:00:00Z');
		const events: Record<string, unknown> = {
			soon: { type: 'singleInstance', start: { dateTime: '2027-01-10T09:00:00.0000000' } },
			later: { type: 'singleInstance', start: { dateTime: '2028-01-10T09:00:00.0000000' } },
			weekly: { type: 'seriesMaster', start: { dateTime: '2026-01-10T09:00:00.0000000' } }
		};
		stub((url) => {
			const id = decodeURIComponent(url.pathname.split('/').pop()!);
			return events[id] ? Response.json(events[id]) : new Response('{}', { status: 404 });
		});
		expect(await microsoft.changeIsFar!(creds, 'soon', nearEnd)).toBe(false);
		expect(await microsoft.changeIsFar!(creds, 'later', nearEnd)).toBe(true);
		expect(await microsoft.changeIsFar!(creds, 'weekly', nearEnd)).toBe(true);
		expect(await microsoft.changeIsFar!(creds, 'gone', nearEnd)).toBe(true);
		expect(await microsoft.changeIsFar!(creds, null, nearEnd)).toBe(true);
	});
});
