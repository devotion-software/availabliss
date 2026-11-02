import { afterEach, beforeEach, describe, expect, spyOn, test } from 'bun:test';
import { error, redirect, type RequestEvent } from '@sveltejs/kit';
import { APIError } from 'better-auth';
import { z } from 'zod';
import { ApiError, authed, badRequest, body, codeFor, conflict, forbidden, handler, noContent, notFound, query, redirectTo } from './http';

const event = (over: { userId?: string; body?: string; url?: string } = {}) =>
	({
		request: new Request(over.url ?? 'http://localhost/api/v1/x', {
			method: over.body === undefined ? 'GET' : 'POST',
			body: over.body
		}),
		url: new URL(over.url ?? 'http://localhost/api/v1/x'),
		params: { id: 'p1' },
		locals: { user: over.userId ? { id: over.userId } : null }
	}) as unknown as RequestEvent;

const run = (fn: () => unknown, e = event()) => handler(async () => (await fn()) as Response)(e as never) as Promise<Response>;

let consoleError: ReturnType<typeof spyOn>;
beforeEach(() => {
	consoleError = spyOn(console, 'error').mockImplementation(() => {});
});
afterEach(() => consoleError.mockRestore());

describe('ApiError', () => {
	test('statuses per code', () => {
		expect(notFound().status).toBe(404);
		expect(notFound().message).toBe('Not found');
		expect(forbidden().status).toBe(403);
		expect(conflict('x').status).toBe(409);
		expect(badRequest('x').code).toBe('validation_error');
		expect(new ApiError('upgrade_required', 'Pro').status).toBe(402);
		expect(new ApiError('rate_limited', 'Slow').status).toBe(429);
		expect(new ApiError('unauthorized', 'x').status).toBe(401);
		expect(new ApiError('server_error', 'x').status).toBe(500);
	});

	test('codeFor maps statuses back, unknown → server_error', () => {
		expect(codeFor(404)).toBe('not_found');
		expect(codeFor(402)).toBe('upgrade_required');
		expect(codeFor(429)).toBe('rate_limited');
		expect(codeFor(418)).toBe('server_error');
	});
});

describe('handler', () => {
	test('passes successful responses through', async () => {
		const res = await run(() => new Response('ok', { status: 201 }));
		expect(res.status).toBe(201);
		expect(await res.text()).toBe('ok');
	});

	test('ApiError → its status and JSON body', async () => {
		const res = await run(() => {
			throw new ApiError('validation_error', 'Bad', [{ path: 'name', message: 'Required' }]);
		});
		expect(res.status).toBe(400);
		expect(await res.json()).toEqual({ message: 'Bad', code: 'validation_error', issues: [{ path: 'name', message: 'Required' }] });

		const plain = await run(() => {
			throw new ApiError('upgrade_required', 'Upgrade to Pro');
		});
		expect(plain.status).toBe(402);
		expect(await plain.json()).toEqual({ message: 'Upgrade to Pro', code: 'upgrade_required' });
	});

	test('ZodError → 400 with every issue path', async () => {
		const schema = z.object({ name: z.string().min(1), range: z.object({ from: z.string() }) });
		const res = await run(() => schema.parse({ name: '', range: {} }));
		expect(res.status).toBe(400);
		const json = await res.json();
		expect(json.code).toBe('validation_error');
		expect(json.issues.map((i: { path: string }) => i.path)).toEqual(['name', 'range.from']);
		expect(json.message).toBe(json.issues[0].message);
	});

	test('Better Auth APIError → its status and matching code', async () => {
		const res = await run(() => {
			throw new APIError('FORBIDDEN', { message: 'Nope' });
		});
		expect(res.status).toBe(403);
		expect(await res.json()).toEqual({ message: 'Nope', code: 'forbidden' });

		const limited = await run(() => {
			throw new APIError('TOO_MANY_REQUESTS');
		});
		expect(limited.status).toBe(429);
		expect((await limited.json()).code).toBe('rate_limited');
	});

	test('anything else → 500 without leaking the message', async () => {
		const res = await run(() => {
			throw new Error('db password is hunter2');
		});
		expect(res.status).toBe(500);
		expect(await res.json()).toEqual({ message: 'Something went wrong', code: 'server_error' });
		expect(consoleError).toHaveBeenCalled();
	});

	test('SvelteKit redirects and http errors are rethrown', async () => {
		await expect(
			run(() => {
				redirect(303, '/elsewhere');
			})
		).rejects.toMatchObject({ status: 303, location: '/elsewhere' });
		await expect(
			run(() => {
				error(418, 'teapot');
			})
		).rejects.toMatchObject({ status: 418 });
	});
});

describe('authed', () => {
	test('401 without a user', async () => {
		const res = (await authed(() => new Response('x'))(event() as never)) as Response;
		expect(res.status).toBe(401);
		expect(await res.json()).toEqual({ message: 'Sign in to continue', code: 'unauthorized' });
	});

	test('passes the user id, params and url', async () => {
		let seen: unknown;
		const res = (await authed((ctx) => {
			seen = { userId: ctx.userId, params: ctx.params, path: ctx.url.pathname };
			return noContent();
		})(event({ userId: 'u1' }) as never)) as Response;
		expect(res.status).toBe(204);
		expect(seen).toEqual({ userId: 'u1', params: { id: 'p1' }, path: '/api/v1/x' });
	});
});

describe('body / query', () => {
	const schema = z.object({ n: z.int() });

	test('parses a valid JSON body', async () => {
		expect(await body(event({ body: '{"n":3}' }), schema)).toEqual({ n: 3 });
	});

	test('non-JSON body is a validation error', async () => {
		const err = await body(event({ body: 'not json' }), schema).catch((e) => e);
		expect(err).toBeInstanceOf(ApiError);
		expect(err.code).toBe('validation_error');
		expect(err.message).toBe('Expected a JSON body');
	});

	test('schema failures throw ZodError', async () => {
		await expect(body(event({ body: '{"n":"3"}' }), schema)).rejects.toBeInstanceOf(z.ZodError);
	});

	test('query reads search params', () => {
		const url = new URL('http://x/?from=2026-11-01&to=2026-11-30&extra=1');
		expect(query(url, z.object({ from: z.string(), to: z.string() }))).toEqual({ from: '2026-11-01', to: '2026-11-30' });
		expect(() => query(new URL('http://x/'), z.object({ from: z.string() }))).toThrow(z.ZodError);
	});
});

describe('redirectTo', () => {
	const thrown = (location: string) => {
		try {
			redirectTo(location);
		} catch (e) {
			return e as { status: number; location: string };
		}
		throw new Error('did not redirect');
	};

	test('app paths, external URLs and app schemes', () => {
		expect(thrown('/groups')).toMatchObject({ status: 302, location: '/groups' });
		expect(thrown('https://accounts.google.com/o/oauth2')).toMatchObject({ status: 302, location: 'https://accounts.google.com/o/oauth2' });
		expect(thrown('availabliss://auth?ok=1')).toMatchObject({ status: 302, location: 'availabliss://auth?ok=1' });
		expect(thrown('//evil.example')).toMatchObject({ status: 302, location: '//evil.example' });
	});
});
