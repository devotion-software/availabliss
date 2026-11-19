/**
 * The API contract: one entry per endpoint. scripts/openapi.ts turns it into docs/openapi.json
 * (`bun run openapi`), and api-contract.test.ts fails when a route under routes/api/v1 is added
 * or removed without updating it.
 *
 * - Bodies and queries are the zod schemas the routes validate with (from schemas.ts), so they
 *   can't disagree with the code. A few that aren't validated with zod (redirect flows, raw
 *   uploads, webhooks) are described here instead.
 * - Responses name a type from types.ts or api-types.ts, or are a small JSON Schema built with the
 *   helpers below (mostly `{ things: Thing[] }` envelopes).
 * - Summaries, auth and errors follow docs/API.md, which stays the prose guide.
 */
import { z } from 'zod';
import * as s from './schemas';

export type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

/**
 * Who can call it: `none` (no session), `user` (signed in: cookie or bearer token),
 * `admin` (a group admin or the owner) or `owner` (the group's owner).
 */
export type Auth = 'none' | 'user' | 'admin' | 'owner';

/** A JSON Schema fragment. A bare string names a type in types.ts / api-types.ts. */
export type Shape = string | JsonSchema;
export interface JsonSchema {
	[key: string]: unknown;
}

/** A non-JSON body or response (image bytes, a multipart upload, an .ics file, an event stream). */
export interface RawContent {
	contentType: string;
	description: string;
	schema?: JsonSchema;
}

export type SuccessStatus = 200 | 201 | 202 | 204 | 302;
export type ErrorStatus = 400 | 401 | 402 | 403 | 404 | 409 | 429 | 503;

export interface Endpoint {
	method: Method;
	/** OpenAPI style, relative to /api/v1 (e.g. `/groups/{id}/proposals`), or the full path when `root`. */
	path: string;
	/** Served outside /api/v1 (`/calendar/...`, `/media/...`, `/api/auth/...`). */
	root?: boolean;
	tag: string;
	summary: string;
	description?: string;
	auth: Auth;
	body?: z.ZodType | RawContent;
	query?: z.ZodType;
	/** Defaults to 200. */
	status?: SuccessStatus;
	/** Required unless `status` is 202, 204 or 302. */
	response?: Shape | RawContent;
	/** Errors worth knowing about beyond the usual (400 with a body or query, 401 when signed in, 403 for admin / owner, 429). */
	errors?: Partial<Record<ErrorStatus, string>>;
	/** A tighter limit than the default per-user budget (docs/API.md, Rate limits). */
	rateLimit?: string;
}

// ── Schema helpers ───────────────────────────────────────────────────────────

export const ref = (name: string): JsonSchema => ({ $ref: `#/components/schemas/${name}` });
const schema = (shape: Shape): JsonSchema => (typeof shape === 'string' ? ref(shape) : shape);
const str: JsonSchema = { type: 'string' };
const num: JsonSchema = { type: 'number' };
const list = (item: Shape): JsonSchema => ({ type: 'array', items: schema(item) });
const nullable = (shape: Shape): JsonSchema => ({ anyOf: [schema(shape), { type: 'null' }] });
/** An object with these properties, all required. */
const obj = (props: Record<string, Shape>): JsonSchema => ({
	type: 'object',
	properties: Object.fromEntries(Object.entries(props).map(([k, v]) => [k, schema(v)])),
	required: Object.keys(props)
});

const imageUpload: RawContent = {
	contentType: 'image/*',
	description: 'The image bytes (JPEG / PNG / WebP / HEIC, at most 10 MB) with their image/* content type.',
	schema: { type: 'string', contentMediaType: 'image/*' }
};
const icsFile = (description: string): RawContent => ({ contentType: 'text/calendar', description, schema: str });
const signedLinkQuery = z.object({
	e: z.string().optional().describe('Expiry, from the signed link'),
	s: z.string().optional().describe('Signature, from the signed link')
});

// Bodies of the Better Auth endpoints the apps call (served by Better Auth, not validated here).
const email = z.email();
const betterAuth = {
	sendOtp: z.object({ email, type: z.literal('sign-in') }),
	signInOtp: z.object({ email, otp: z.string() }),
	signInSocial: z.object({
		provider: z.enum(['google', 'apple']),
		idToken: z.object({ token: z.string(), nonce: z.string().optional() }).optional().describe('Native: the SDK id token'),
		callbackURL: z.string().optional().describe('Web: where to land after the redirect')
	}),
	linkSocial: z.object({ provider: z.enum(['google', 'apple']), callbackURL: z.string(), errorCallbackURL: z.string().optional() }),
	passkeyResponse: z.object({ response: z.record(z.string(), z.unknown()).describe('AuthenticationResponseJSON / RegistrationResponseJSON') }),
	passkeyRegister: z.object({ response: z.record(z.string(), z.unknown()), name: z.string().optional() })
};
const betterAuthResult: JsonSchema = {
	type: 'object',
	description: "Better Auth's response; sign-ins include `user` and send the bearer token in the `set-auth-token` header."
};

// ── Endpoints ────────────────────────────────────────────────────────────────

// One endpoint per line reads as a table; Prettier would explode each into a block.
// prettier-ignore
export const endpoints: Endpoint[] = [
	// Health
	{ method: 'GET', path: '/health', tag: 'Health', auth: 'none', summary: 'Server health', response: 'Health', errors: { 503: "The database doesn't answer (Redis being down is still 200)" } },
	{ method: 'POST', path: '/csp-report', tag: 'Health', auth: 'none', summary: 'Content-Security-Policy violation report (from browsers)', body: { contentType: 'application/csp-report', description: 'A CSP report (`application/csp-report` or Reporting API JSON)' }, status: 204 },

	// Sign-in (Better Auth, outside /api/v1) — summarised: see docs/API.md, Sign-in flows.
	{ method: 'POST', root: true, path: '/api/auth/email-otp/send-verification-otp', tag: 'Auth', auth: 'none', summary: 'Email a one-time sign-in code', body: betterAuth.sendOtp, response: betterAuthResult, rateLimit: '5 / minute per IP and 5 / 15 minutes per email address' },
	{ method: 'POST', root: true, path: '/api/auth/sign-in/email-otp', tag: 'Auth', auth: 'none', summary: 'Sign in with the emailed code', body: betterAuth.signInOtp, response: betterAuthResult, rateLimit: '10 / minute per IP' },
	{ method: 'POST', root: true, path: '/api/auth/sign-in/social', tag: 'Auth', auth: 'none', summary: 'Sign in with Google or Apple (native id token, or a web redirect)', body: betterAuth.signInSocial, response: betterAuthResult },
	{ method: 'GET', root: true, path: '/api/auth/passkey/generate-authenticate-options', tag: 'Auth', auth: 'none', summary: 'Passkey sign-in: WebAuthn options', response: betterAuthResult },
	{ method: 'POST', root: true, path: '/api/auth/passkey/verify-authentication', tag: 'Auth', auth: 'none', summary: 'Passkey sign-in: verify the assertion', body: betterAuth.passkeyResponse, response: betterAuthResult },
	{ method: 'GET', root: true, path: '/api/auth/passkey/generate-register-options', tag: 'Auth', auth: 'user', summary: 'Add a passkey: WebAuthn options', response: betterAuthResult },
	{ method: 'POST', root: true, path: '/api/auth/passkey/verify-registration', tag: 'Auth', auth: 'user', summary: 'Add a passkey: verify the attestation', body: betterAuth.passkeyRegister, response: betterAuthResult },
	{ method: 'POST', root: true, path: '/api/auth/link-social', tag: 'Auth', auth: 'user', summary: 'Web: link Google or Apple with a redirect', body: betterAuth.linkSocial, response: betterAuthResult },
	{ method: 'POST', root: true, path: '/api/auth/sign-out', tag: 'Auth', auth: 'user', summary: 'Sign out', response: betterAuthResult },

	// Native web-flow sign-in (PKCE)
	{
		method: 'GET', path: '/auth/native/{provider}/start', tag: 'Auth', auth: 'none', summary: 'Native web-flow sign-in: start (opened in a browser sheet)',
		query: z.object({ code_challenge: z.string().describe('base64url(SHA-256(code_verifier))') }),
		status: 302, description: "Redirects to Apple / Google; the flow ends at /auth/native/callback, which redirects to `availabliss://auth/callback?code=` (or `?error=`). `provider` is `apple` or `google`.",
		errors: { 404: 'Unknown provider' }
	},
	{ method: 'GET', path: '/auth/native/callback', tag: 'Auth', auth: 'none', summary: 'Native web-flow sign-in: callback', status: 302, description: 'Redirects to `availabliss://auth/callback?code=<one-time code>` or `?error=`.' },
	{ method: 'POST', path: '/auth/native/exchange', tag: 'Auth', auth: 'none', summary: 'Swap the one-time code for a bearer token', body: s.nativeExchangeSchema, response: 'NativeToken', rateLimit: '10 / minute per IP' },

	// Me
	{ method: 'GET', path: '/me', tag: 'Me', auth: 'user', summary: 'Your account', response: 'User' },
	{ method: 'PATCH', path: '/me', tag: 'Me', auth: 'user', summary: 'Update your account', body: s.updateMeSchema, response: 'User', errors: { 409: 'That username is taken' } },
	{ method: 'DELETE', path: '/me', tag: 'Me', auth: 'user', summary: 'Delete your account and all its data', status: 204 },
	{ method: 'GET', path: '/usernames/{username}', tag: 'Me', auth: 'user', summary: 'Whether a username can be yours, and who has it', response: 'UsernameCheck', rateLimit: '60 / minute' },
	{ method: 'PUT', path: '/me/avatar', tag: 'Me', auth: 'user', summary: 'Set your profile picture', body: imageUpload, response: 'User' },
	{ method: 'DELETE', path: '/me/avatar', tag: 'Me', auth: 'user', summary: 'Remove your profile picture', response: 'User' },
	{ method: 'GET', path: '/me/busy', tag: 'Me', auth: 'user', summary: 'Your own merged busy time', query: s.busyQuerySchema, response: obj({ intervals: list('BusyInterval') }) },
	{ method: 'POST', path: '/me/busy/ignores', tag: 'Me', auth: 'user', summary: 'Mark a calendar event (or its series) not busy', body: s.busyIgnoreSchema, status: 201, response: obj({ id: str }), errors: { 404: 'Calendar not found' } },
	{ method: 'DELETE', path: '/me/busy/ignores/{id}', tag: 'Me', auth: 'user', summary: 'Count it as busy again', status: 204 },
	{ method: 'GET', path: '/me/accounts', tag: 'Me', auth: 'user', summary: 'Linked Apple / Google sign-ins', response: 'LinkedAccounts' },
	{ method: 'POST', path: '/me/accounts/link', tag: 'Me', auth: 'user', summary: 'Link Apple / Google with a native id token', body: s.linkAccountSchema, response: 'LinkResult', errors: { 409: 'That sign-in has its own account (`AccountConflict`): send again with `merge: true` to merge it' } },
	{ method: 'POST', path: '/me/accounts/merge-intent', tag: 'Me', auth: 'user', summary: 'Web: remember a merge before signing in with the provider (sets a cookie)', body: s.mergeIntentSchema, status: 204 },
	{ method: 'GET', path: '/me/notification-settings', tag: 'Notifications', auth: 'user', summary: 'Your notification settings', response: 'NotificationSettings' },
	{ method: 'PATCH', path: '/me/notification-settings', tag: 'Notifications', auth: 'user', summary: 'Update your notification settings', body: s.notificationSettingsSchema, response: 'NotificationSettings' },
	{ method: 'GET', path: '/me/plans', tag: 'Events', auth: 'user', summary: 'Every plan across your groups', response: 'MyPlans' },
	{ method: 'GET', path: '/me/plans.ics', tag: 'Events', auth: 'user', summary: 'Every plan you are going to, as one .ics', response: icsFile('Every plan in `going` from GET /me/plans') },
	{ method: 'GET', path: '/me/upcoming', tag: 'Events', auth: 'user', summary: 'Events in the next 60 days, all groups', response: obj({ events: list('UpcomingEvent') }) },
	{ method: 'GET', path: '/me/calendar-feed', tag: 'Calendar feed', auth: 'user', summary: 'Your secret calendar feed link (made the first time)', response: 'CalendarFeed' },
	{ method: 'POST', path: '/me/calendar-feed/reset', tag: 'Calendar feed', auth: 'user', summary: 'A new feed link; the old one stops working', response: 'CalendarFeed' },
	{
		method: 'GET', root: true, path: '/calendar/{file}', tag: 'Calendar feed', auth: 'none', summary: 'Subscribable calendar feed (`<token>.ics`)',
		query: z.object({ group: z.string().optional().describe('Only this group') }),
		response: icsFile("Your groups' plans from 60 days back"), errors: { 404: 'Unknown or reset link' }
	},

	// Plans (Pro)
	{ method: 'GET', path: '/me/plan', tag: 'Billing', auth: 'user', summary: 'Your plan (free / Pro)', response: 'Plan' },
	{ method: 'POST', path: '/me/plan/refresh', tag: 'Billing', auth: 'user', summary: 'Re-read RevenueCat after a purchase or restore', response: 'Plan' },
	{
		method: 'POST', path: '/billing/revenuecat', tag: 'Billing', auth: 'none', summary: 'RevenueCat webhook',
		description: '`Authorization` must equal REVENUECAT_WEBHOOK_AUTH.',
		body: { contentType: 'application/json', description: 'A RevenueCat webhook `{ event }`', schema: { type: 'object' } },
		response: obj({ ok: { type: 'boolean', const: true } }), errors: { 401: 'Wrong Authorization header' }
	},

	// AI planner
	{ method: 'POST', path: '/groups/{id}/planner', tag: 'Planner', auth: 'user', summary: 'AI planner: best time, alternatives, ideas and a checklist (Pro)', body: s.plannerSchema, response: 'PlannerResult', errors: { 402: 'Needs Pro', 409: 'No time works yet' }, rateLimit: '20 / hour' },
	{ method: 'POST', path: '/groups/{id}/planner/draft', tag: 'Planner', auth: 'user', summary: 'AI-drafted proposal title and message (Pro)', body: s.plannerDraftSchema, response: 'ProposalDraft', errors: { 402: 'Needs Pro', 404: "AI isn't set up on this server" }, rateLimit: '30 / hour' },

	// Timing templates
	{ method: 'GET', path: '/me/templates', tag: 'Templates', auth: 'user', summary: 'Your saved timings (sorted by name)', response: obj({ templates: list('RequirementTemplate') }) },
	{ method: 'POST', path: '/me/templates', tag: 'Templates', auth: 'user', summary: 'Save a timing (at most 50)', body: s.createTemplateSchema, status: 201, response: 'RequirementTemplate' },
	{ method: 'PATCH', path: '/me/templates/{id}', tag: 'Templates', auth: 'user', summary: 'Rename or change a saved timing', body: s.updateTemplateSchema, response: 'RequirementTemplate' },
	{ method: 'DELETE', path: '/me/templates/{id}', tag: 'Templates', auth: 'user', summary: 'Delete a saved timing', status: 204 },

	// Regions & holidays
	{ method: 'GET', path: '/regions/countries', tag: 'Regions', auth: 'none', summary: 'Countries', response: obj({ countries: list('Region') }) },
	{ method: 'GET', path: '/regions/subdivisions', tag: 'Regions', auth: 'none', summary: "A country's subdivisions", query: s.countryQuerySchema, response: obj({ subdivisions: list('Region') }) },
	{ method: 'GET', path: '/regions/school-authorities', tag: 'Regions', auth: 'none', summary: "A country's school authorities", query: s.countryQuerySchema, response: obj({ authorities: list('SchoolAuthority') }) },
	{ method: 'GET', path: '/regions/detect', tag: 'Regions', auth: 'none', summary: 'Best-effort region from the request', response: 'DetectedRegion', rateLimit: '30 / minute per IP' },
	{ method: 'GET', path: '/holidays', tag: 'Regions', auth: 'none', summary: 'Bank holidays (at most 400 days)', query: s.holidaysQuerySchema, response: obj({ holidays: list('Holiday') }) },

	// Calendars
	{ method: 'GET', path: '/calendars', tag: 'Calendars', auth: 'user', summary: 'Your calendar connections', response: obj({ connections: list('CalendarConnection') }) },
	{
		method: 'GET', path: '/calendars/oauth/{provider}/start', tag: 'Calendars', auth: 'user', summary: 'Web: redirect to Google / Microsoft consent',
		query: z.object({ redirect: z.string().optional().describe('Where to land afterwards (a web path)') }), status: 302, errors: { 404: 'Unknown provider' }
	},
	{ method: 'POST', path: '/calendars/oauth/{provider}/url', tag: 'Calendars', auth: 'user', summary: 'Native: the consent URL to open in a browser sheet', body: s.oauthUrlSchema, response: obj({ url: str }), errors: { 404: 'Unknown provider' }, rateLimit: '10 / 10 minutes' },
	{
		method: 'GET', path: '/calendars/oauth/{provider}/callback', tag: 'Calendars', auth: 'none', summary: 'OAuth callback (called by the provider)',
		query: z.object({ state: z.string().optional(), code: z.string().optional(), error: z.string().optional() }),
		status: 302, description: 'Web: links the calendar, then redirects with `?connected=` or `?error=`. Native: redirects to `availabliss://…?complete=<code>&provider=`.'
	},
	{ method: 'POST', path: '/calendars/oauth/{provider}/complete', tag: 'Calendars', auth: 'user', summary: 'Native: finish a Google / Microsoft connection', body: s.oauthCompleteSchema, response: 'CalendarConnection', errors: { 403: 'A different user started the flow', 404: 'Unknown provider' }, rateLimit: '10 / 10 minutes' },
	{ method: 'POST', path: '/calendars/caldav', tag: 'Calendars', auth: 'user', summary: 'Connect a CalDAV calendar (iCloud by default)', body: s.caldavSchema, status: 201, response: 'CalendarConnection', rateLimit: '10 / 10 minutes' },
	{ method: 'PUT', path: '/calendars/device', tag: 'Calendars', auth: 'user', summary: "Upload busy time from the phone's calendars", body: s.deviceSyncSchema, response: 'CalendarConnection', rateLimit: '30 / minute' },
	{ method: 'PATCH', path: '/calendars/{connectionId}/calendars/{calendarId}', tag: 'Calendars', auth: 'user', summary: 'Whether a calendar counts as busy', body: s.calendarBusySchema, response: 'CalendarConnection' },
	{ method: 'POST', path: '/calendars/{connectionId}/sync', tag: 'Calendars', auth: 'user', summary: 'Sync now', response: 'CalendarConnection', rateLimit: '6 / minute' },
	{ method: 'DELETE', path: '/calendars/{connectionId}', tag: 'Calendars', auth: 'user', summary: 'Disconnect a calendar', status: 204 },
	{
		method: 'POST', path: '/calendars/push/google', tag: 'Calendars', auth: 'none', summary: 'Google Calendar change notification (called by Google)',
		description: 'No body: `X-Goog-Channel-ID`, `X-Goog-Channel-Token` and `X-Goog-Resource-State` headers.', response: { contentType: 'text/plain', description: 'Empty' }
	},
	{
		method: 'POST', path: '/calendars/push/microsoft', tag: 'Calendars', auth: 'none', summary: 'Microsoft Graph change notifications (called by Microsoft)',
		query: z.object({ validationToken: z.string().optional().describe("Graph's check when a subscription is created: echoed as text/plain with 200") }),
		body: { contentType: 'application/json', description: 'Graph change notifications `{ value: [...] }`', schema: { type: 'object' } },
		status: 202
	},

	// Manual busy blocks
	{ method: 'GET', path: '/blocks', tag: 'Blocks', auth: 'user', summary: 'Your busy blocks ("My time")', response: obj({ blocks: list('ManualBlock') }) },
	{ method: 'POST', path: '/blocks', tag: 'Blocks', auth: 'user', summary: 'Add a busy block', body: s.createBlockSchema, status: 201, response: 'ManualBlock' },
	{ method: 'PATCH', path: '/blocks/{id}', tag: 'Blocks', auth: 'user', summary: 'Change a busy block', body: s.updateBlockSchema, response: 'ManualBlock' },
	{ method: 'DELETE', path: '/blocks/{id}', tag: 'Blocks', auth: 'user', summary: 'Delete a busy block', status: 204 },

	// Groups
	{ method: 'GET', path: '/groups', tag: 'Groups', auth: 'user', summary: 'Your groups', response: obj({ groups: list('GroupSummary') }) },
	{ method: 'POST', path: '/groups', tag: 'Groups', auth: 'user', summary: 'Create a group (you are the owner)', body: s.createGroupSchema, status: 201, response: 'Group', errors: { 402: 'A free account already owns 3 groups' }, rateLimit: '20 / hour' },
	{ method: 'GET', path: '/groups/{id}', tag: 'Groups', auth: 'user', summary: 'A group', response: 'Group' },
	{ method: 'PATCH', path: '/groups/{id}', tag: 'Groups', auth: 'admin', summary: 'Change a group (name, colour, timing, archived, rhythm …)', body: s.updateGroupSchema, response: 'Group' },
	{ method: 'DELETE', path: '/groups/{id}', tag: 'Groups', auth: 'owner', summary: 'Delete a group', status: 204 },
	{ method: 'PUT', path: '/groups/{id}/image', tag: 'Groups', auth: 'admin', summary: "Set the group's photo", body: imageUpload, response: 'Group' },
	{ method: 'DELETE', path: '/groups/{id}/image', tag: 'Groups', auth: 'admin', summary: "Remove the group's photo", response: 'Group' },
	{ method: 'GET', path: '/groups/{id}/next', tag: 'Groups', auth: 'user', summary: "The free time nearest the group's next meet-up (rhythm)", response: obj({ suggestion: nullable('NextSuggestion') }) },
	{ method: 'PUT', path: '/groups/{id}/mute', tag: 'Notifications', auth: 'user', summary: 'Mute a group', body: s.muteGroupSchema, response: 'NotificationSettings' },
	{ method: 'DELETE', path: '/groups/{id}/mute', tag: 'Notifications', auth: 'user', summary: 'Unmute a group', response: 'NotificationSettings' },
	{ method: 'POST', path: '/groups/{id}/nudge', tag: 'Groups', auth: 'admin', summary: "Nudge members who haven't synced (once per 12 hours)", response: obj({ nudged: num }) },
	{ method: 'POST', path: '/groups/{id}/leave', tag: 'Members', auth: 'user', summary: 'Leave a group (the owner names the new owner)', body: s.leaveGroupSchema, status: 204, errors: { 409: "Nobody else is an admin, or you're the only member" } },
	{ method: 'PATCH', path: '/groups/{id}/members/{userId}', tag: 'Members', auth: 'user', summary: "Change a member's role (admins) or name in the group (yourself, or an admin for anyone)", body: s.memberUpdateSchema, response: 'Member' },
	{
		method: 'DELETE', path: '/groups/{id}/members/{userId}', tag: 'Members', auth: 'user', summary: 'Remove a member (admin), or yourself (leave)',
		description: 'An admin removes someone (never the owner); anyone can remove themselves.', status: 204
	},
	{ method: 'PATCH', path: '/groups/{id}/members/me', tag: 'Members', auth: 'user', summary: 'The name you go by in this group', body: s.memberNameSchema, response: 'Member' },
	{ method: 'GET', path: '/groups/{id}/preferences', tag: 'Preferences', auth: 'user', summary: "Every member's preferred times", response: obj({ preferences: list('GroupPreference') }) },
	{ method: 'POST', path: '/groups/{id}/preferences', tag: 'Preferences', auth: 'user', summary: 'Add a preferred time', body: s.preferenceSchema, status: 201, response: 'GroupPreference' },
	{ method: 'DELETE', path: '/groups/{id}/preferences/{prefId}', tag: 'Preferences', auth: 'user', summary: 'Delete one of your preferred times', status: 204 },

	// Invites
	{ method: 'GET', path: '/groups/{id}/invites', tag: 'Invites', auth: 'admin', summary: 'Live invites, newest first', response: obj({ invites: list('GroupInvite') }) },
	{ method: 'POST', path: '/groups/{id}/invites', tag: 'Invites', auth: 'admin', summary: 'Invite by link, email, username or user id', body: s.inviteSchema, status: 201, response: 'InvitesCreated', errors: { 404: 'A username or user id nobody (you can invite) has', 409: 'Someone is already in the group' }, rateLimit: '30 / hour' },
	{ method: 'DELETE', path: '/groups/{id}/invites/{inviteId}', tag: 'Invites', auth: 'admin', summary: 'Revoke an invite', status: 204 },
	{ method: 'GET', path: '/invites/{token}', tag: 'Invites', auth: 'none', summary: 'Preview an invite', response: 'InvitePreview', errors: { 404: 'Expired, revoked or used up' }, rateLimit: '30 / 10 minutes per IP' },
	{ method: 'POST', path: '/invites/{token}/accept', tag: 'Invites', auth: 'user', summary: 'Join with an invite', response: 'JoinedGroup', errors: { 403: 'The invite is for someone else', 404: 'Expired, revoked or used up' }, rateLimit: '30 / 10 minutes per IP' },
	{ method: 'POST', path: '/invites/{token}/decline', tag: 'Invites', auth: 'user', summary: 'Decline an invite sent to you', status: 204, errors: { 403: "Someone else's invite, or a link" } },

	// Availability
	{ method: 'POST', path: '/requirements/preview', tag: 'Availability', auth: 'user', summary: 'How many slots a timing produces', body: s.requirementPreviewSchema, response: 'RequirementPreview', rateLimit: '120 / minute' },
	{ method: 'GET', path: '/groups/{id}/availability', tag: 'Availability', auth: 'user', summary: "A group's availability between two dates", query: s.rangeQuerySchema, response: 'Availability', rateLimit: '120 / minute' },
	{ method: 'GET', path: '/groups/{id}/availability/day', tag: 'Availability', auth: 'user', summary: "One day's busy time per member", query: s.dayQuerySchema, response: 'DayAvailability', rateLimit: '120 / minute' },
	{ method: 'GET', path: '/groups/{id}/conflicts', tag: 'Availability', auth: 'user', summary: "Who's busy at a time", query: s.conflictsQuerySchema, response: obj({ busyMemberIds: list(str) }) },

	// Proposals
	{ method: 'GET', path: '/groups/{id}/proposals', tag: 'Proposals', auth: 'user', summary: "A group's proposals", query: s.proposalsQuerySchema, response: obj({ proposals: list('Proposal') }) },
	{ method: 'POST', path: '/groups/{id}/proposals', tag: 'Proposals', auth: 'user', summary: 'Propose a time (posts a chat card)', body: s.createProposalSchema, status: 201, response: 'Proposal' },
	{ method: 'GET', path: '/proposals/{id}', tag: 'Proposals', auth: 'user', summary: 'A proposal', response: 'Proposal' },
	{ method: 'POST', path: '/proposals/{id}/respond', tag: 'Proposals', auth: 'user', summary: 'Reply to a proposal', body: s.respondSchema, response: 'Proposal', errors: { 409: 'Cancelled, or you joined after it was confirmed' } },
	{ method: 'POST', path: '/proposals/{id}/override', tag: 'Proposals', auth: 'admin', summary: "Set someone's reply", body: s.overrideSchema, response: 'Proposal', errors: { 409: 'No longer open' } },
	{ method: 'POST', path: '/proposals/{id}/confirm', tag: 'Proposals', auth: 'user', summary: 'Confirm anyway (creator or admin)', response: 'Proposal', errors: { 403: 'Not the creator or an admin', 409: 'Not open, or nobody else has accepted' } },
	{ method: 'POST', path: '/proposals/{id}/keep', tag: 'Proposals', auth: 'user', summary: 'Keep the plan after a drop-out (creator or admin)', response: 'Proposal', errors: { 403: 'Not the creator or an admin', 409: 'Not confirmed' } },
	{ method: 'DELETE', path: '/proposals/{id}', tag: 'Proposals', auth: 'user', summary: 'Cancel a proposal or plan (creator or admin)', status: 204, errors: { 403: 'Not the creator or an admin', 409: 'Already cancelled' } },

	// Events
	{ method: 'GET', path: '/groups/{id}/events', tag: 'Events', auth: 'user', summary: "A group's events (default: 30 days back to a year ahead)", query: s.eventsQuerySchema, response: obj({ events: list('GroupEvent') }) },
	{ method: 'POST', path: '/groups/{id}/events', tag: 'Events', auth: 'user', summary: 'Add an event straight to the calendar', body: s.createEventSchema, status: 201, response: 'GroupEvent' },
	{ method: 'GET', path: '/events/{id}', tag: 'Events', auth: 'user', summary: 'An event', response: 'GroupEvent' },
	{ method: 'PATCH', path: '/events/{id}', tag: 'Events', auth: 'user', summary: 'Change an event (creator or admin)', body: s.updateEventSchema, response: 'GroupEvent', errors: { 403: 'Not the creator or an admin' } },
	{ method: 'DELETE', path: '/events/{id}', tag: 'Events', auth: 'user', summary: 'Delete an event (creator or admin)', status: 204, errors: { 403: 'Not the creator or an admin' } },
	{ method: 'PUT', path: '/events/{id}/image', tag: 'Events', auth: 'user', summary: "Set an event's cover photo (creator or admin)", body: imageUpload, response: 'GroupEvent', errors: { 403: 'Not the creator or an admin' } },
	{ method: 'DELETE', path: '/events/{id}/image', tag: 'Events', auth: 'user', summary: "Remove an event's cover photo (creator or admin)", response: 'GroupEvent', errors: { 403: 'Not the creator or an admin' } },
	{ method: 'GET', path: '/events/{id}/ics', tag: 'Events', auth: 'user', summary: 'An event as an .ics file', response: icsFile('The event') },

	// Chat
	{ method: 'GET', path: '/groups/{id}/messages', tag: 'Chat', auth: 'user', summary: 'A page of messages (newest last)', query: s.messagesQuerySchema, response: 'MessagePage' },
	{
		method: 'POST', path: '/groups/{id}/messages', tag: 'Chat', auth: 'user', summary: 'Send a message, photos / files, a place, or a poll',
		body: s.createMessageSchema, status: 201, response: 'Message', errors: { 409: 'Replying to a deleted message' }, rateLimit: '30 / minute'
	},
	{
		method: 'POST', path: '/groups/{id}/attachments', tag: 'Chat', auth: 'user', summary: 'Upload a photo, GIF or file to send in a message',
		body: {
			contentType: 'multipart/form-data',
			description: 'One file in the field `file`',
			schema: { type: 'object', properties: { file: { type: 'string', contentMediaType: 'application/octet-stream' } }, required: ['file'] }
		},
		status: 201, response: 'Attachment', rateLimit: '60 / 10 minutes'
	},
	{ method: 'POST', path: '/groups/{id}/messages/receipts', tag: 'Chat', auth: 'user', summary: 'Mark messages delivered or seen', body: s.receiptsSchema, status: 204 },
	{ method: 'PATCH', path: '/messages/{id}', tag: 'Chat', auth: 'user', summary: 'Edit your own text message', body: s.editMessageSchema, response: 'Message' },
	{ method: 'DELETE', path: '/messages/{id}', tag: 'Chat', auth: 'user', summary: 'Delete your own text message', status: 204 },
	{ method: 'GET', path: '/messages/{id}/info', tag: 'Chat', auth: 'user', summary: 'Who has seen a message', response: 'MessageInfo' },
	{ method: 'GET', path: '/messages/{id}/thread', tag: 'Chat', auth: 'user', summary: 'The thread a message is in', response: 'MessageThread' },
	{
		method: 'PATCH', path: '/polls/{id}', tag: 'Chat', auth: 'user', summary: "Edit a poll (its author): question, pick-one / pick-any, a text poll's options",
		body: s.updatePollSchema, response: 'Poll', errors: { 403: 'Not the author', 409: 'The poll is closed' }
	},
	{ method: 'POST', path: '/polls/{id}/vote', tag: 'Chat', auth: 'user', summary: 'Vote in a poll (empty removes your vote)', body: s.voteSchema, response: 'Poll', errors: { 409: 'The poll is closed' } },
	{ method: 'POST', path: '/polls/{id}/close', tag: 'Chat', auth: 'user', summary: "Close a poll (its author)", response: 'Poll', errors: { 403: 'Not the author' } },
	{
		method: 'GET', path: '/attachments/{id}', tag: 'Chat', auth: 'none', summary: "An attachment's file (signed link, or a signed-in member)",
		query: signedLinkQuery.extend({ download: z.literal('1').optional().describe('Download a photo instead of showing it') }),
		response: { contentType: '*/*', description: 'The file', schema: { type: 'string', contentMediaType: 'application/octet-stream' } }, errors: { 404: 'No valid signature and not a member' }
	},
	{
		method: 'GET', path: '/attachments/{id}/thumb', tag: 'Chat', auth: 'none', summary: "A photo's preview (signed link, or a signed-in member)",
		query: signedLinkQuery, response: { contentType: 'image/*', description: 'The preview', schema: { type: 'string', contentMediaType: 'image/*' } }, errors: { 404: 'No valid signature and not a member' }
	},
	{
		method: 'GET', root: true, path: '/media/{key}', tag: 'Chat', auth: 'none', summary: 'A profile picture, group photo or event cover (signed link)',
		description: '`key` may contain slashes. The links come from `User.image`, `Group.image`, `GroupEvent.image` and so on.',
		query: signedLinkQuery, response: { contentType: 'image/jpeg', description: 'The picture', schema: { type: 'string', contentMediaType: 'image/jpeg' } }, errors: { 404: 'No valid signature' }
	},

	// Live updates
	{
		method: 'GET', path: '/live', tag: 'Live', auth: 'user', summary: 'Server-Sent Events for every group you are in',
		response: { contentType: 'text/event-stream', description: 'Each event is `data: <LiveEvent JSON>`', schema: { type: 'string' } }, rateLimit: '20 connections / minute'
	},

	// Notifications & devices
	{ method: 'GET', path: '/notifications', tag: 'Notifications', auth: 'user', summary: 'Your Activity', response: 'NotificationList' },
	{ method: 'POST', path: '/notifications/read', tag: 'Notifications', auth: 'user', summary: 'Mark notifications read (no ids: all except chats)', body: s.readNotificationsSchema, status: 204 },
	{ method: 'POST', path: '/devices', tag: 'Notifications', auth: 'user', summary: 'Register an APNs / FCM token', body: s.deviceSchema, status: 204 },
	{ method: 'DELETE', path: '/devices/{token}', tag: 'Notifications', auth: 'user', summary: 'Unregister a push token', status: 204 }
];

/** `METHOD /path` (with the /api/v1 prefix left off unless `root`), for messages and lookups. */
export const endpointKey = (e: Pick<Endpoint, 'method' | 'path'>) => `${e.method} ${e.path}`;
