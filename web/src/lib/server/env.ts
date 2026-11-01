import 'dotenv/config';

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
	authSecret: optional('BETTER_AUTH_SECRET'),
	secretsKey: optional('SECRETS_KEY') ?? optional('BETTER_AUTH_SECRET'),

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
 * WebAuthn origins Android uses for passkeys created by the app: the base64url
 * SHA-256 of each signing certificate (configured as colon-separated hex).
 */
export const androidPasskeyOrigins = env.android.sha256Fingerprints.map(
	(hex) => `android:apk-key-hash:${Buffer.from(hex.replace(/:/g, ''), 'hex').toString('base64url')}`
);
