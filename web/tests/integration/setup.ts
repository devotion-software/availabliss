/**
 * Imported first by every integration test, before anything reads `env`.
 * dotenv never overrides a variable that is already set, so blanking these keeps
 * the tests off the network: email goes to the console, RevenueCat webhooks use
 * the event body, and the AI planner reports itself unavailable.
 */
for (const name of ['RESEND_API_KEY', 'SMTP_HOST', 'REVENUECAT_SECRET_KEY', 'ANTHROPIC_API_KEY', 'APNS_KEY_ID', 'APNS_KEY', 'FCM_SERVICE_ACCOUNT']) {
	process.env[name] = '';
}

// Email falls back to console.log; keep the test output readable.
const log = console.log;
console.log = (...args: unknown[]) => {
	if (typeof args[0] === 'string' && args[0].includes('[email]')) return;
	log(...args);
};
