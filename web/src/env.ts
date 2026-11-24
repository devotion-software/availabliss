/**
 * Environment variables SvelteKit hands to the browser ($app/env/public). Everything else the
 * server reads straight from process.env (src/lib/server/env.ts).
 */
import { defineEnvVars } from '@sveltejs/kit/env';

const optional = (value: string | undefined) => value?.trim() || undefined;

export const variables = defineEnvVars({
	PUBLIC_SENTRY_DSN: {
		public: true,
		schema: optional,
		description: 'DSN of the Sentry project for the web app (server and browser). Unset keeps Sentry off.'
	},
	PUBLIC_SENTRY_ENVIRONMENT: {
		public: true,
		schema: optional,
		description: 'Sentry environment name. Defaults to production in a production build, development otherwise.'
	}
});
