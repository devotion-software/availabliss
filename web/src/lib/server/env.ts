import './dotenv';

/** Typed access to runtime configuration. Optional integrations return null when unset. */
function optional(name: string): string | null {
	const value = process.env[name];
	return value && value.trim() ? value.trim() : null;
}

function list(name: string): string[] {
	return (optional(name) ?? '')
		.split(',')
		.map((v) => v.trim())
		.filter(Boolean);
}

export const env = {
	baseUrl: optional('BETTER_AUTH_URL') ?? 'http://localhost:5190',
	/** Extra origins Better Auth accepts, e.g. the Android emulator's http://10.0.2.2:5190 in dev. */
	trustedOrigins: list('TRUSTED_ORIGINS'),
	authSecret: optional('BETTER_AUTH_SECRET'),
	secretsKey: optional('SECRETS_KEY') ?? optional('BETTER_AUTH_SECRET'),
	/** Shared state for multiple instances (live updates, sign-in codes, rate limits, locks). */
	redisUrl: optional('REDIS_URL'),
	/** Rate limiting: on in production, off in dev unless RATE_LIMIT=on. RATE_LIMIT=off disables it anywhere. */
	rateLimit: (optional('RATE_LIMIT') ?? (process.env.NODE_ENV === 'production' ? 'on' : 'off')) !== 'off',

	google: {
		/** Web client first; iOS / Android client ids are accepted for native ID-token sign-in. */
		clientIds: list('GOOGLE_CLIENT_IDS'),
		clientSecret: optional('GOOGLE_CLIENT_SECRET')
	},
	apple: {
		/** Services ID used by the web flow. */
		clientId: optional('APPLE_CLIENT_ID'),
		clientSecret: optional('APPLE_CLIENT_SECRET'),
		teamId: optional('APPLE_TEAM_ID'),
		bundleId: optional('IOS_BUNDLE_ID')
	},
	microsoft: {
		clientId: optional('MICROSOFT_CLIENT_ID'),
		clientSecret: optional('MICROSOFT_CLIENT_SECRET')
	},
	android: {
		package: optional('ANDROID_PACKAGE'),
		sha256Fingerprints: list('ANDROID_SHA256_FINGERPRINTS')
	},
	apns: {
		keyId: optional('APNS_KEY_ID'),
		/** Contents of the .p8 key; literal "\n" sequences are converted to newlines. */
		key: optional('APNS_KEY')?.replace(/\\n/g, '\n') ?? null,
		production: optional('APNS_PRODUCTION') === 'true'
	},
	/** Base64-encoded Firebase service account JSON. */
	fcmServiceAccount: optional('FCM_SERVICE_ACCOUNT'),

	/** Optional AI planner (Pro). Off unless a key is set. */
	ai: {
		apiKey: optional('ANTHROPIC_API_KEY'),
		model: optional('AI_PLANNER_MODEL') ?? 'claude-opus-5-5'
	},
	revenuecat: {
		/** Secret (v1 REST) key: the server re-reads a subscriber after every webhook. */
		secretKey: optional('REVENUECAT_SECRET_KEY'),
		/** The Authorization header value set on the RevenueCat webhook. */
		webhookAuth: optional('REVENUECAT_WEBHOOK_AUTH'),
		/** Public SDK keys handed to the apps / web page (not secret). */
		iosKey: optional('REVENUECAT_IOS_KEY'),
		androidKey: optional('REVENUECAT_ANDROID_KEY'),
		webKey: optional('REVENUECAT_WEB_KEY'),
		entitlement: optional('REVENUECAT_ENTITLEMENT') ?? 'pro'
	},

	email: {
		from: optional('EMAIL_FROM') ?? 'Availabliss <no-reply@availabliss.local>',
		resendApiKey: optional('RESEND_API_KEY'),
		smtpHost: optional('SMTP_HOST'),
		smtpPort: Number(optional('SMTP_PORT') ?? 587),
		smtpSecure: optional('SMTP_SECURE') === 'true',
		smtpUser: optional('SMTP_USER'),
		smtpPassword: optional('SMTP_PASSWORD')
	}
} as const;

export const isProduction = process.env.NODE_ENV === 'production';

/**
 * The key for our own HMAC signatures (OAuth state, the native flow cookie).
 * Development falls back to a fixed key; production refuses to sign with a key
 * anyone could guess.
 */
export function signingSecret(): string {
	if (env.authSecret) return env.authSecret;
	if (process.env.NODE_ENV === 'production') throw new Error('BETTER_AUTH_SECRET is not set');
	return 'dev';
}

/**
 * WebAuthn origins Android uses for passkeys created by the app: the base64url
 * SHA-256 of each signing certificate (configured as colon-separated hex).
 */
export const androidPasskeyOrigins = env.android.sha256Fingerprints.map(
	(hex) => `android:apk-key-hash:${Buffer.from(hex.replace(/:/g, ''), 'hex').toString('base64url')}`
);
