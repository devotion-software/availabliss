/**
 * The one typed wrapper around `fetch` for the /api/v1 JSON API (docs/API.md).
 * The web authenticates with the Better Auth session cookie, so requests are
 * plain same-origin fetches. Load functions pass SvelteKit's `fetch` so calls
 * work during SSR and are deduplicated on hydration.
 */
import type { ApiErrorBody } from '#lib/types';

export const API_BASE = '/api/v1';

export type Fetch = typeof fetch;
export type Query = Record<string, string | number | boolean | null | undefined>;

export interface RequestOptions {
	/** SvelteKit's `fetch` inside load functions; the global one otherwise. */
	fetch?: Fetch;
	query?: Query;
	signal?: AbortSignal;
}

const CODE_BY_STATUS: Record<number, ApiErrorBody['code']> = {
	400: 'validation_error',
	401: 'unauthorized',
	403: 'forbidden',
	404: 'not_found',
	409: 'conflict',
	429: 'rate_limited'
};

/** A non-2xx API response, carrying the documented error body. */
export class ApiError extends Error {
	readonly status: number;
	readonly code: ApiErrorBody['code'];
	readonly issues: NonNullable<ApiErrorBody['issues']>;

	constructor(status: number, body: Partial<ApiErrorBody> | null) {
		super(body?.message || 'Something went wrong. Please try again.');
		this.name = 'ApiError';
		this.status = status;
		this.code = body?.code ?? CODE_BY_STATUS[status] ?? 'server_error';
		this.issues = body?.issues ?? [];
	}

	/** The validation message for one field, if the server flagged it. */
	issue(path: string): string | undefined {
		return this.issues.find((i) => i.path === path)?.message;
	}
}

/** A readable message for any thrown value, for toasts and inline errors. */
export function errorMessage(err: unknown): string {
	if (err instanceof ApiError) return err.message;
	if (err instanceof TypeError) return "Couldn't reach Availabliss. Check your connection.";
	if (err instanceof Error && err.message) return err.message;
	return 'Something went wrong. Please try again.';
}

/** `/groups` + `{ from, to }` → `/api/v1/groups?from=…&to=…` */
export function apiUrl(path: string, query?: Query): string {
	const params = new URLSearchParams();
	for (const [key, value] of Object.entries(query ?? {})) {
		if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
	}
	const qs = params.toString();
	return `${API_BASE}${path}${qs ? `?${qs}` : ''}`;
}

async function request<T>(method: string, path: string, body: unknown, opts: RequestOptions = {}): Promise<T> {
	const doFetch = opts.fetch ?? fetch;
	const res = await doFetch(apiUrl(path, opts.query), {
		method,
		credentials: 'same-origin',
		signal: opts.signal,
		headers: {
			accept: 'application/json',
			...(body !== undefined && { 'content-type': 'application/json' })
		},
		body: body === undefined ? undefined : JSON.stringify(body)
	});

	if (!res.ok) {
		const errBody = (await res.json().catch(() => null)) as Partial<ApiErrorBody> | null;
		throw new ApiError(res.status, errBody);
	}
	// Reading response headers inside SSR load fetches isn't allowed unless they're
	// whitelisted, so detect empty bodies from the text instead.
	if (res.status === 204) return undefined as T;
	const text = await res.text();
	return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
	get: <T>(path: string, opts?: RequestOptions) => request<T>('GET', path, undefined, opts),
	post: <T = void>(path: string, body?: unknown, opts?: RequestOptions) => request<T>('POST', path, body ?? {}, opts),
	put: <T>(path: string, body: unknown, opts?: RequestOptions) => request<T>('PUT', path, body, opts),
	patch: <T>(path: string, body: unknown, opts?: RequestOptions) => request<T>('PATCH', path, body, opts),
	del: (path: string, opts?: RequestOptions) => request<void>('DELETE', path, undefined, opts)
};
