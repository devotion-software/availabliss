/** Settings shared by the server (instrumentation.server.ts) and browser (hooks.client.ts) Sentry setup. */

/** Matches the release the Vite plugin uploads source maps under (vite.config.ts). */
export const SENTRY_RELEASE = `availabliss-web@${__APP_VERSION__}`;

/** PUBLIC_SENTRY_ENVIRONMENT if set, otherwise production or development. */
export function sentryEnvironment(configured: string | undefined, production: boolean): string {
	return configured?.trim() || (production ? 'production' : 'development');
}

/** Every trace outside production; one in ten in production, to keep within quota. */
export function tracesSampleRate(environment: string): number {
	return environment === 'production' ? 0.1 : 1;
}

/**
 * What Sentry may collect beyond the error and its stack. Its defaults send cookies, headers,
 * bodies and query strings, which here would be session cookies, bearer tokens, signed media
 * links, chat text and planner prompts. So none of that: the user is identified by id only
 * (hooks.server.ts), and only the route and timing of requests are kept.
 */
export const DATA_COLLECTION = {
	userInfo: false,
	cookies: false,
	httpHeaders: false,
	httpBodies: [],
	urlQueryParams: false,
	genAI: { inputs: false, outputs: false },
	databaseQueryData: false,
	stackFrameVariables: false
};
