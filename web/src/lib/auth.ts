import { betterAuth } from 'better-auth';
import { and, eq, isNull } from 'drizzle-orm';
import { drizzleAdapter } from 'better-auth/adapters/drizzle';
import { bearer, emailOTP } from 'better-auth/plugins';
import { passkey } from '@better-auth/passkey';
import { db } from './server/db';
import * as schema from './server/db/schema';
import { androidPasskeyOrigins, env } from './server/env';
import { sendOtpEmail } from './server/email';
import { kv } from './server/redis';
import { AUTH_RULES, OTP_ATTEMPTS, consumeAuthLimit } from './server/rate-limit';
import { assignUsername } from './server/usernames';

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
	// Profile changes go through PATCH /me (validated) and PUT /me/avatar; Better Auth's own
	// update-user would let anyone set any `image` URL (then deleted as "theirs" by DELETE /me/avatar).
	// Password resets and email verification by code aren't used (sign-in by code verifies the
	// address); left on, they'd email only existing accounts, so their timing would tell who has one.
	disabledPaths: [
		'/update-user',
		'/email-otp/request-password-reset',
		'/forget-password/email-otp',
		'/email-otp/reset-password',
		'/email-otp/verify-email',
		'/email-otp/check-verification-otp'
	],
	socialProviders: socialProviders(),
	// Apple's web flow posts back from appleid.apple.com; TRUSTED_ORIGINS adds dev hosts.
	trustedOrigins: ['https://appleid.apple.com', ...env.trustedOrigins],
	rateLimit: {
		enabled: env.rateLimit,
		customRules: AUTH_RULES,
		// Counters live in the shared store (Redis) so limits hold across instances. With Redis
		// down the store counts in memory instead, so the limits still hold on each instance.
		customStorage: { consume: (key, rule) => consumeAuthLimit(kv, key, rule) }
	},
	account: {
		accountLinking: {
			enabled: true,
			// Signing in with Apple links to an existing account with the same address straight away
			// (Apple always verifies addresses). Google only does when it says the address is verified.
			trustedProviders: ['apple'],
			// Linking from You / settings: the Apple or Google address often differs from the
			// account's (Hide My Email, a personal Gmail). The person is signed in and proves the
			// other sign-in, so a different address is fine.
			allowDifferentEmails: true
		}
	},
	databaseHooks: {
		user: {
			create: {
				// Everyone gets a username straight away (changed in the sign-up wizard or Settings).
				async after(created) {
					await assignUsername(created.id);
				}
			}
		},
		account: {
			create: {
				// Linking Google (or signing up with it) gives a profile photo: use it when the
				// account has none yet. Apple doesn't share a photo.
				async after(account) {
					if (account.providerId !== 'google' || !account.idToken) return;
					const picture = googlePicture(account.idToken);
					if (!picture) return;
					await db
						.update(schema.user)
						.set({ image: picture })
						.where(and(eq(schema.user.id, account.userId), isNull(schema.user.image)));
				}
			}
		}
	},
	user: {
		additionalFields: {
			username: { type: 'string', required: false, input: false },
			themeMode: { type: 'string', required: false, defaultValue: 'system', input: false },
			colorTheme: { type: 'string', required: false, defaultValue: 'dawn', input: false },
			country: { type: 'string', required: false, input: false },
			subdivision: { type: 'string', required: false, input: false },
			schoolAuthority: { type: 'string', required: false, input: false },
			timezone: { type: 'string', required: false, defaultValue: 'Europe/London', input: false },
			onboarded: { type: 'boolean', required: false, defaultValue: false, input: false },
			busyBufferMinutes: { type: 'number', required: false, defaultValue: 0, input: false },
			eventReminders: { type: 'string', required: false, defaultValue: 'both', input: false },
			showAddToCalendar: { type: 'boolean', required: false, defaultValue: true, input: false },
			autoArchiveNewGroups: { type: 'boolean', required: false, defaultValue: false, input: false }
		}
	},
	plugins: [
		emailOTP({
			otpLength: 6,
			expiresIn: 10 * 60,
			// Wrong guesses per code. Counted with the code in the database, so this backstop holds
			// whatever happens to Redis: a fresh code is needed after three misses.
			allowedAttempts: OTP_ATTEMPTS,
			// Only a hash of each code is kept, so reading the database doesn't give live codes.
			storeOTP: 'hashed',
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

/** The `picture` claim of a Google ID token (only read, not verified: Better Auth already did). */
function googlePicture(idToken: string): string | null {
	try {
		const payload = JSON.parse(Buffer.from(idToken.split('.')[1], 'base64url').toString('utf8'));
		return typeof payload.picture === 'string' && payload.picture.startsWith('https://') ? payload.picture : null;
	} catch {
		return null;
	}
}
