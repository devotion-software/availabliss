/**
 * Sentry for the server (API, pages, background jobs). SvelteKit loads this file before any other
 * server code, so Sentry's tracing is in place before the app's modules are imported. Without
 * PUBLIC_SENTRY_DSN the SDK stays off. What it may collect is limited
 * by DATA_COLLECTION (#lib/sentry).
 */
import '#lib/server/dotenv';
import * as Sentry from '@sentry/sveltekit';
import { DATA_COLLECTION, SENTRY_RELEASE, sentryEnvironment, tracesSampleRate } from '#lib/sentry';

const dsn = process.env.PUBLIC_SENTRY_DSN?.trim();

if (dsn && process.env.NODE_ENV !== 'test') {
	const environment = sentryEnvironment(process.env.PUBLIC_SENTRY_ENVIRONMENT, process.env.NODE_ENV === 'production');
	Sentry.init({
		dsn,
		environment,
		release: SENTRY_RELEASE,
		tracesSampleRate: tracesSampleRate(environment),
		dataCollection: DATA_COLLECTION
	});
}
