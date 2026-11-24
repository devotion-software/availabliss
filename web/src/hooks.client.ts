/**
 * Sentry in the browser: errors, page-load and navigation traces, and session replays. Without
 * PUBLIC_SENTRY_DSN the SDK stays off. Replays mask all text and inputs and block images and
 * video, so availability, chat and calendar details never leave the browser; only the page's
 * structure and clicks are recorded.
 */
import * as Sentry from '@sentry/sveltekit';
import { dev } from '$app/env';
import { PUBLIC_SENTRY_DSN, PUBLIC_SENTRY_ENVIRONMENT } from '$app/env/public';
import { DATA_COLLECTION, SENTRY_RELEASE, sentryEnvironment, tracesSampleRate } from '#lib/sentry';

const dsn = PUBLIC_SENTRY_DSN;

if (dsn) {
	const environment = sentryEnvironment(PUBLIC_SENTRY_ENVIRONMENT, !dev);
	Sentry.init({
		dsn,
		environment,
		release: SENTRY_RELEASE,
		dataCollection: DATA_COLLECTION,
		tracesSampleRate: tracesSampleRate(environment),
		// Trace headers only go to our own API, never to RevenueCat, Stripe or map tiles.
		tracePropagationTargets: [/^\//, window.location.origin],
		integrations: [Sentry.replayIntegration({ maskAllText: true, maskAllInputs: true, blockAllMedia: true })],
		// A tenth of sessions are recorded, and every session that hits an error.
		replaysSessionSampleRate: environment === 'production' ? 0.1 : 0,
		replaysOnErrorSampleRate: 1
	});
}

export const handleError = Sentry.handleErrorWithSentry();
