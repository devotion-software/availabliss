import { defineConfig, devices } from '@playwright/test';

/**
 * Browser end-to-end tests for the web app's critical paths (e2e/). They run against a server that's
 * already up: BASE_URL (default the dev server) with Mailpit at MAILPIT_URL for sign-in codes. In CI
 * the Smoke job runs them against the production build it starts.
 */
export default defineConfig({
	testDir: 'e2e',
	timeout: 60_000,
	expect: { timeout: 10_000 },
	fullyParallel: false,
	// One browser at a time: the flows sign up new accounts against one server.
	workers: 1,
	retries: process.env.CI ? 1 : 0,
	reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
	use: {
		baseURL: process.env.BASE_URL ?? 'http://localhost:5190',
		trace: 'retain-on-failure',
		screenshot: 'only-on-failure'
	},
	projects: [
		{ name: 'phone', use: { ...devices['iPhone 15'], browserName: 'chromium' } },
		{ name: 'desktop', use: { ...devices['Desktop Chrome'] } }
	]
});
