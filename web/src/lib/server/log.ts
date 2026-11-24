/**
 * Server logging: one line per entry, JSON in production (level, time, scope, message, request id,
 * user id and any fields; errors as name / message / stack) so a log viewer can filter on them, and
 * a readable line in development. Request ids come from `withRequestContext` (hooks.server.ts).
 * `log.error` also reports to Sentry (a no-op when it isn't set up), since most of those are failures
 * that were caught, such as background jobs, and would otherwise never reach it.
 */
import { AsyncLocalStorage } from 'node:async_hooks';
import * as Sentry from '@sentry/sveltekit';

export type Level = 'debug' | 'info' | 'warn' | 'error';
type Fields = Record<string, unknown>;

interface RequestContext {
	requestId: string;
	userId: string | null;
	method?: string;
	path?: string;
}

const context = new AsyncLocalStorage<RequestContext>();

/** Runs `fn` with a request's id and user attached to everything it logs. */
export function withRequestContext<T>(ctx: RequestContext, fn: () => T): T {
	return context.run(ctx, fn);
}

/** The current request's context, if any (to add the user once it's known). */
export const requestContext = () => context.getStore();

const json = process.env.LOG_FORMAT ? process.env.LOG_FORMAT === 'json' : process.env.NODE_ENV === 'production';

/** An error as plain fields: name, message, a short stack, and an HTTP status if it has one. */
export function errorFields(err: unknown): Fields {
	if (err instanceof Error) {
		const status = (err as { status?: unknown; statusCode?: unknown }).status ?? (err as { statusCode?: unknown }).statusCode;
		return {
			name: err.name,
			message: err.message,
			...(status !== undefined && { status }),
			stack: err.stack
				?.split('\n')
				.slice(1, 6)
				.map((l) => l.trim())
		};
	}
	return { message: String(err) };
}

function write(level: Level, scope: string, message: string, fields: Fields = {}) {
	const ctx = context.getStore();
	const { err, ...rest } = fields;
	const entry: Fields = {
		level,
		time: new Date().toISOString(),
		scope,
		message,
		...(ctx && { requestId: ctx.requestId, userId: ctx.userId ?? undefined }),
		...rest,
		...(err !== undefined && { error: errorFields(err) })
	};
	const out = level === 'error' ? console.error : level === 'warn' ? console.warn : console.log;
	if (json) {
		out(JSON.stringify(entry));
	} else {
		const extra = Object.keys(rest).length ? ` ${JSON.stringify(rest)}` : '';
		const error = err !== undefined ? ` ${err instanceof Error ? (err.stack ?? err.message) : String(err)}` : '';
		out(`[${scope}] ${message}${extra}${error}`);
	}
}

/** An error log as a Sentry event: the error itself if there is one, otherwise the message. */
function report(scope: string, message: string, fields: Fields = {}) {
	const { err, ...rest } = fields;
	const context = { tags: { scope }, extra: { message, ...rest } };
	if (err !== undefined) Sentry.captureException(err, context);
	else Sentry.captureMessage(`[${scope}] ${message}`, { level: 'error', ...context });
}

/** `log.warn('calendars', 'sync failed', { provider, connectionId, err })`; `err` is formatted as an error. */
export const log = {
	debug: (scope: string, message: string, fields?: Fields) => write('debug', scope, message, fields),
	info: (scope: string, message: string, fields?: Fields) => write('info', scope, message, fields),
	warn: (scope: string, message: string, fields?: Fields) => write('warn', scope, message, fields),
	error: (scope: string, message: string, fields?: Fields) => {
		write('error', scope, message, fields);
		report(scope, message, fields);
	}
};
