import { sentrySvelteKit } from '@sentry/sveltekit/vite';
import adapter from '@sveltejs/adapter-node';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';
import pkg from './package.json' with { type: 'json' };

/**
 * Script sources besides our own: RevenueCat Web Billing loads its checkout (Stripe or Paddle)
 * from these hosts, and Svelte's server rendering puts `onload` / `onerror="this.__e=event"` on
 * images (to replay those events after hydration), allowed by that exact handler's hash only.
 */
const SCRIPT_HOSTS = [
	'https://js.stripe.com',
	'https://cdn.paddle.com',
	'https://sdk.revenuecat-static.com',
	'unsafe-hashes',
	'sha256-7dQwUgLau1NFCCGjfn9FsYptB6ZtWxJin6VohGIu20I='
] as const;
const BILLING_HOSTS = [
	'https://api.revenuecat.com',
	'https://e.revenue.cat',
	'https://*.revenuecat.com',
	'https://*.stripe.com',
	'https://*.paddle.com'
] as const;
/** Social sign-in redirects through these after a form post. */
const SIGN_IN_HOSTS = ['https://accounts.google.com', 'https://appleid.apple.com', 'https://login.microsoftonline.com'] as const;
/** Where the browser SDK sends errors, traces and replays (US and EU regions). */
const SENTRY_HOSTS = ['https://*.ingest.sentry.io', 'https://*.ingest.us.sentry.io', 'https://*.ingest.de.sentry.io'] as const;

/**
 * Source maps are uploaded to Sentry only when SENTRY_AUTH_TOKEN (plus SENTRY_ORG and
 * SENTRY_PROJECT) is set at build time, and are then deleted from the build so they aren't served.
 */
const uploadSourceMaps = Boolean(process.env.SENTRY_AUTH_TOKEN);

// Says in the build log whether the upload will happen (the Sentry plugin is silent when it won't).
// Never prints the token itself.
if (process.argv.includes('build')) {
	const missing = ['SENTRY_AUTH_TOKEN', 'SENTRY_ORG', 'SENTRY_PROJECT'].filter((name) => !process.env[name]);
	console.log(
		missing.length === 0
			? `[sentry] Uploading source maps for availabliss-web@${pkg.version} to ${process.env.SENTRY_ORG}/${process.env.SENTRY_PROJECT}`
			: `[sentry] Not uploading source maps: ${missing.join(', ')} not set for the build`
	);
}

export default defineConfig({
	define: {
		__APP_VERSION__: JSON.stringify(pkg.version)
	},
	plugins: [
		// Must come before the SvelteKit plugin.
		sentrySvelteKit({
			org: process.env.SENTRY_ORG,
			project: process.env.SENTRY_PROJECT,
			authToken: process.env.SENTRY_AUTH_TOKEN,
			autoUploadSourceMaps: uploadSourceMaps,
			release: { name: `availabliss-web@${pkg.version}`, create: uploadSourceMaps, finalize: uploadSourceMaps },
			telemetry: false
		}),
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) => (filename.split(/[/\\]/).includes('node_modules') ? undefined : true)
			},
			adapter: adapter(),
			// Server-side spans for handle, loads, actions and endpoints, sent to Sentry by
			// src/instrumentation.server.ts.
			tracing: { server: true },
			// SvelteKit's origin check also rejects body-less POST/DELETE from the native
			// apps (no Origin header). hooks.server.ts applies a bearer-aware check instead.
			csrf: { trustedOrigins: ['*'] },
			// Content-Security-Policy. SvelteKit adds a nonce (or a hash, on prerendered pages) to its
			// own scripts and to app.html's inline script. Enforced: where scripts may come from, no
			// plugins, no <base> hijacking and no framing. The fuller policy (images, connections,
			// frames) is report-only until real traffic shows it doesn't break billing or sign-in;
			// reports go to /api/v1/csp-report and are logged.
			csp: {
				mode: 'auto',
				directives: {
					'script-src': ['self', ...SCRIPT_HOSTS],
					// Sentry's session replay compresses recordings in a worker it starts from a blob.
					// Without this, workers fall back to script-src, which blocks it.
					'worker-src': ['self', 'blob:'],
					'object-src': ['none'],
					'base-uri': ['self'],
					'frame-ancestors': ['none']
				},
				reportOnly: {
					'default-src': ['self'],
					'script-src': ['self', ...SCRIPT_HOSTS],
					'style-src': ['self', 'unsafe-inline', 'https://fonts.googleapis.com'],
					'font-src': ['self', 'https://fonts.gstatic.com'],
					// Profile and group photos (R2 or Google), chat photos, OpenStreetMap tiles.
					'img-src': ['self', 'data:', 'blob:', 'https:'],
					'media-src': ['self', 'blob:', 'https:'],
					'connect-src': ['self', 'https://nominatim.openstreetmap.org', ...BILLING_HOSTS, ...SENTRY_HOSTS],
					// Sentry's session replay compresses recordings in a worker it starts from a blob.
					'worker-src': ['self', 'blob:'],
					'frame-src': ['self', ...BILLING_HOSTS],
					'form-action': ['self', ...SIGN_IN_HOSTS],
					'object-src': ['none'],
					'base-uri': ['self'],
					'frame-ancestors': ['none'],
					'report-uri': ['/api/v1/csp-report']
				}
			}
		})
	],
	server: {
		// IPv4 loopback: the Android emulator reaches the host only via 10.0.2.2 → 127.0.0.1,
		// and Node would otherwise bind "localhost" to ::1 alone. Browsers and the iOS
		// simulator still reach it as localhost. Use `bun run dev --host` for real devices.
		host: '127.0.0.1',
		// Fixed port keeps BETTER_AUTH_URL / OAuth callbacks stable across restarts.
		port: 5190,
		strictPort: true
	}
});
