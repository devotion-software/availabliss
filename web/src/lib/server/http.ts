/**
 * Helpers shared by every /api/v1 handler: consistent JSON errors (docs/API.md
 * "Errors"), request validation and authentication.
 */
import { isHttpError, isRedirect, json, redirect, type RequestEvent, type RequestHandler } from '@sveltejs/kit';
import { APIError } from 'better-auth';
import { z } from 'zod';
import type { ApiErrorBody } from '#lib/types';

type Code = ApiErrorBody['code'];

const STATUS: Record<Code, number> = {
	unauthorized: 401,
	forbidden: 403,
	not_found: 404,
	validation_error: 400,
	conflict: 409,
	upgrade_required: 402,
	rate_limited: 429,
	server_error: 500
};

export class ApiError extends Error {
	constructor(
		readonly code: Code,
		message: string,
		readonly issues?: ApiErrorBody['issues']
	) {
		super(message);
	}

	get status(): number {
		return STATUS[this.code];
	}
}

/** The error code that corresponds to an HTTP status (unknown statuses map to server_error). */
export function codeFor(status: number): Code {
	return (Object.entries(STATUS).find(([, s]) => s === status)?.[0] as Code | undefined) ?? 'server_error';
}

export const notFound = (what = 'Not found') => new ApiError('not_found', what);
export const forbidden = (why = 'You do not have access to this') => new ApiError('forbidden', why);
export const conflict = (why: string) => new ApiError('conflict', why);
export const badRequest = (why: string) => new ApiError('validation_error', why);

function toResponse(err: unknown): Response {
	if (err instanceof ApiError) {
		const body: ApiErrorBody = { message: err.message, code: err.code, ...(err.issues && { issues: err.issues }) };
		return json(body, { status: err.status });
	}
	if (err instanceof APIError) {
		const status = err.statusCode ?? 500;
		return json({ message: err.message, code: codeFor(status) } satisfies ApiErrorBody, { status });
	}
	if (err instanceof z.ZodError) {
		const issues = err.issues.map((i) => ({ path: i.path.join('.'), message: i.message }));
		const body: ApiErrorBody = { message: issues[0]?.message ?? 'Invalid request', code: 'validation_error', issues };
		return json(body, { status: 400 });
	}
	console.error('[api] unhandled error', err);
	return json({ message: 'Something went wrong', code: 'server_error' } satisfies ApiErrorBody, { status: 500 });
}

export interface AuthedEvent<P extends Record<string, string> = Record<string, string>> {
	event: RequestEvent<P>;
	userId: string;
	params: P;
	url: URL;
}

/** Wraps a handler so thrown ApiErrors / ZodErrors become JSON error responses. */
export function handler<P extends Record<string, string>>(
	fn: (event: RequestEvent<P>) => Promise<Response> | Response
): RequestHandler<P> {
	return async (event) => {
		try {
			return await fn(event);
		} catch (err) {
			// SvelteKit's redirect() / error() work by throwing; let the framework handle them.
			if (isRedirect(err) || isHttpError(err)) throw err;
			return toResponse(err);
		}
	};
}

/** Like `handler`, but requires a signed-in user (cookie or bearer token). */
export function authed<P extends Record<string, string>>(
	fn: (ctx: AuthedEvent<P>) => Promise<Response> | Response
): RequestHandler<P> {
	return handler<P>((event) => {
		const userId = event.locals.user?.id;
		if (!userId) throw new ApiError('unauthorized', 'Sign in to continue');
		return fn({ event, userId, params: event.params, url: event.url });
	});
}

/** Parses and validates a JSON body. */
export async function body<S extends z.ZodType>(event: RequestEvent, schema: S): Promise<z.output<S>> {
	let raw: unknown;
	try {
		raw = await event.request.json();
	} catch {
		throw badRequest('Expected a JSON body');
	}
	return schema.parse(raw);
}

/** Parses and validates query-string parameters. */
export function query<S extends z.ZodType>(url: URL, schema: S): z.output<S> {
	return schema.parse(Object.fromEntries(url.searchParams));
}

export const noContent = () => new Response(null, { status: 204 });

/**
 * 302 to an app path, an OAuth provider or the native app's `availabliss://`
 * scheme. SvelteKit only allows non-path targets when marked external.
 */
export function redirectTo(location: string): never {
	redirect(302, location, location.startsWith('/') && !location.startsWith('//') ? undefined : { external: true });
}

export { json };
