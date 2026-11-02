import { afterEach, beforeEach, describe, expect, test } from 'bun:test';
import { api, ApiError, apiUrl, errorMessage } from './api';

type Seen = { url: string; init: RequestInit };
let seen: Seen[] = [];
let reply: () => Response = () => new Response('{}');
const realFetch = globalThis.fetch;

beforeEach(() => {
	seen = [];
	globalThis.fetch = (async (url: string, init: RequestInit) => {
		seen.push({ url, init });
		return reply();
	}) as typeof fetch;
});
afterEach(() => {
	globalThis.fetch = realFetch;
});

const jsonReply = (status: number, body: unknown) => () =>
	new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

describe('apiUrl', () => {
	test('drops empty values and encodes the rest', () => {
		expect(apiUrl('/groups')).toBe('/api/v1/groups');
		expect(apiUrl('/groups', {})).toBe('/api/v1/groups');
		expect(apiUrl('/x', { from: '2026-11-01', n: 0, flag: false, a: null, b: undefined, c: '' })).toBe(
			'/api/v1/x?from=2026-11-01&n=0&flag=false'
		);
		expect(apiUrl('/search', { q: 'a&b c/é' })).toBe('/api/v1/search?q=a%26b+c%2F%C3%A9');
	});
});

describe('api requests', () => {
	test('GET sends no body and parses JSON', async () => {
		reply = jsonReply(200, { id: 'g1' });
		expect(await api.get<{ id: string }>('/groups/g1', { query: { from: '2026-11-01' } })).toEqual({ id: 'g1' });
		expect(seen[0].url).toBe('/api/v1/groups/g1?from=2026-11-01');
		expect(seen[0].init.method).toBe('GET');
		expect(seen[0].init.body).toBeUndefined();
		expect(seen[0].init.credentials).toBe('same-origin');
		expect((seen[0].init.headers as Record<string, string>)['content-type']).toBeUndefined();
	});

	test('POST defaults to an empty JSON object', async () => {
		reply = () => new Response(null, { status: 204 });
		expect(await api.post('/groups/g1/leave')).toBeUndefined();
		expect(seen[0].init.method).toBe('POST');
		expect(seen[0].init.body).toBe('{}');
		expect((seen[0].init.headers as Record<string, string>)['content-type']).toBe('application/json');
	});

	test('PUT / PATCH / DELETE', async () => {
		reply = () => new Response('');
		await api.put('/a', { x: 1 });
		await api.patch('/b', { y: 2 });
		expect(await api.del('/c')).toBeUndefined();
		expect(seen.map((s) => [s.init.method, s.url, s.init.body])).toEqual([
			['PUT', '/api/v1/a', '{"x":1}'],
			['PATCH', '/api/v1/b', '{"y":2}'],
			['DELETE', '/api/v1/c', undefined]
		]);
	});

	test('uses a passed fetch (SvelteKit load)', async () => {
		let used = false;
		const custom = (async () => {
			used = true;
			return new Response('[1]');
		}) as unknown as typeof fetch;
		expect(await api.get('/x', { fetch: custom })).toEqual([1]);
		expect(used).toBe(true);
		expect(seen).toHaveLength(0);
	});
});

describe('errors', () => {
	test('error body is kept, including issues', async () => {
		reply = jsonReply(400, { message: 'Name is required', code: 'validation_error', issues: [{ path: 'name', message: 'Required' }] });
		const err = (await api.post('/groups', {}).catch((e) => e)) as ApiError;
		expect(err).toBeInstanceOf(ApiError);
		expect(err.status).toBe(400);
		expect(err.message).toBe('Name is required');
		expect(err.issue('name')).toBe('Required');
		expect(err.issue('other')).toBeUndefined();
	});

	test('status → code when the body has none', async () => {
		const cases: [number, ApiError['code']][] = [
			[401, 'unauthorized'],
			[402, 'upgrade_required'],
			[403, 'forbidden'],
			[404, 'not_found'],
			[409, 'conflict'],
			[429, 'rate_limited'],
			[502, 'server_error']
		];
		for (const [status, code] of cases) {
			reply = () => new Response('<html>oops</html>', { status });
			const err = (await api.get('/x').catch((e) => e)) as ApiError;
			expect([err.status, err.code]).toEqual([status, code]);
			expect(err.message).toBe('Something went wrong. Please try again.');
			expect(err.issues).toEqual([]);
		}
	});

	test('the body code wins over the status', async () => {
		reply = jsonReply(402, { message: 'Upgrade to Pro for more groups', code: 'upgrade_required' });
		const err = (await api.post('/groups', { name: 'x' }).catch((e) => e)) as ApiError;
		expect(err.code).toBe('upgrade_required');
		expect(err.message).toBe('Upgrade to Pro for more groups');
	});

	test('errorMessage', () => {
		expect(errorMessage(new ApiError(429, { message: 'Slow down' }))).toBe('Slow down');
		expect(errorMessage(new TypeError('Failed to fetch'))).toBe("Couldn't reach Availabliss. Check your connection.");
		expect(errorMessage(new Error('Boom'))).toBe('Boom');
		expect(errorMessage(new Error(''))).toBe('Something went wrong. Please try again.');
		expect(errorMessage('string')).toBe('Something went wrong. Please try again.');
	});
});
