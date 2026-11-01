import { betterAuth } from 'better-auth';
import { drizzleAdapter } from 'better-auth/adapters/drizzle';
import { bearer, emailOTP } from 'better-auth/plugins';
import { passkey } from '@better-auth/passkey';
import { db } from './server/db';
import * as schema from './server/db/schema';
import { androidPasskeyOrigins, env } from './server/env';
import { sendOtpEmail } from './server/email';

const rpID = new URL(env.baseUrl).hostname;

/** Only register social providers whose credentials are configured. */
function socialProviders() {
	const providers: Parameters<typeof betterAuth>[0]['socialProviders'] = {};
	if (env.google.clientIds.length && env.google.clientSecret) {
		providers.google = { clientId: env.google.clientIds, clientSecret: env.google.clientSecret };
	}
	if (env.apple.clientId && env.apple.clientSecret) {
		providers.apple = {
			clientId: env.apple.clientId,
			clientSecret: env.apple.clientSecret,
			// Native Sign in with Apple tokens are issued for the app's bundle id.
			appBundleIdentifier: env.apple.bundleId ?? undefined
		};
	}
	return providers;
}

export const auth = betterAuth({
	baseURL: env.baseUrl,
	secret: env.authSecret ?? undefined,
	database: drizzleAdapter(db, { provider: 'mysql', schema }),
	// Passwordless only.
	emailAndPassword: { enabled: false },
	socialProviders: socialProviders(),
	// Apple's web flow posts back from appleid.apple.com; TRUSTED_ORIGINS adds dev hosts.
	trustedOrigins: ['https://appleid.apple.com', ...env.trustedOrigins],
	account: {
		accountLinking: {
			enabled: true,
			trustedProviders: ['google', 'apple', 'email-otp']
		}
	},
	user: {
		additionalFields: {
			themeMode: { type: 'string', required: false, defaultValue: 'system', input: false },
			colorTheme: { type: 'string', required: false, defaultValue: 'dawn', input: false },
			country: { type: 'string', required: false, input: false },
			subdivision: { type: 'string', required: false, input: false },
			schoolAuthority: { type: 'string', required: false, input: false },
			timezone: { type: 'string', required: false, defaultValue: 'Europe/London', input: false },
			onboarded: { type: 'boolean', required: false, defaultValue: false, input: false }
		}
	},
	plugins: [
		emailOTP({
			otpLength: 6,
			expiresIn: 10 * 60,
			sendVerificationOnSignUp: true,
			async sendVerificationOTP({ email, otp, type }) {
				await sendOtpEmail({ email, otp, type });
			}
		}),
		// The Android app signs passkey ceremonies with its own origin, not the website's.
		passkey({ rpID, rpName: 'Availabliss', origin: [env.baseUrl, ...androidPasskeyOrigins] }),
		// Native apps authenticate with `Authorization: Bearer <token>`; the token is
		// returned in the `set-auth-token` header of every sign-in response.
		bearer()
	]
});

export type Session = typeof auth.$Infer.Session.session;
export type User = typeof auth.$Infer.Session.user;
