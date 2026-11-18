/**
 * Request validation shared by the API handlers and the web forms.
 * Each schema mirrors a request body in docs/API.md.
 */
import { z } from 'zod';
import { COLOR_THEMES, REPEATS, RESPONSE_KINDS, THEME_MODES, TILE_COLORS, type Weekday } from './types';
import { isValidZone } from './timezone';
import { normalizeUsername, usernameProblem } from './usernames';

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;

export const dateString = z.string().regex(DATE, 'Expected YYYY-MM-DD');
export const timeString = z.string().regex(TIME, 'Expected HH:MM');
export const isoDateTime = z.iso.datetime({ offset: true });
/** An IANA zone such as `Europe/London`; an invalid one would make Intl throw everywhere it's used. */
export const timezone = z.string().min(1).max(64).refine(isValidZone, { message: 'Unknown time zone' });

/** Longest span the busy-time and conflict endpoints will expand (matches the availability cap). */
export const MAX_INSTANT_RANGE_DAYS = 120;

/** `{ [fromKey], [toKey] }` ISO instants with to > from and at most `maxDays` apart. */
export function instantRangeSchema<F extends string, T extends string>(fromKey: F, toKey: T, maxDays = MAX_INSTANT_RANGE_DAYS) {
	return z
		.object({ [fromKey]: isoDateTime, [toKey]: isoDateTime } as Record<F | T, typeof isoDateTime>)
		.transform((r) => {
			const raw = r as Record<string, string>;
			return { from: Date.parse(raw[fromKey]), to: Date.parse(raw[toKey]) };
		})
		.refine((r) => r.to > r.from, { message: 'The end must be after the start' })
		.refine((r) => r.to - r.from <= maxDays * 86_400_000, { message: `Ask for at most ${maxDays} days at a time` });
}
const weekday = z.int().min(0).max(6).transform((d) => d as Weekday);
const trimmed = (max: number) => z.string().trim().min(1).max(max);
const optionalText = (max: number) => z.string().trim().max(max).nullish().transform((v) => v || null);

export const dateRangeSchema = z
	.object({ from: dateString, to: dateString })
	.refine((r) => r.from <= r.to, { message: 'Range ends before it starts' });

/** Drops repeats of an identical range (first one wins), so a doubled-up list from an older app still saves. */
export function uniqueRanges<R extends { from: string; to: string }>(ranges: R[]): R[] {
	const seen = new Set<string>();
	return ranges.filter((r) => {
		const key = `${r.from}/${r.to}`;
		if (seen.has(key)) return false;
		seen.add(key);
		return true;
	});
}
const dateRangeList = z.array(dateRangeSchema).max(50).transform(uniqueRanges);

export const timeWindowSchema = z
	.object({ from: timeString, to: timeString })
	.refine((w) => w.from < w.to, { message: 'Window ends before it starts' });

export const requirementSchema = z.object({
	length: z.discriminatedUnion('type', [
		z.object({ type: z.literal('duration'), minutes: z.int().min(15).max(24 * 60) }),
		z.object({ type: z.literal('days'), count: z.int().min(1).max(31) }),
		z.object({
			type: z.literal('span'),
			startDay: weekday,
			startTime: timeString,
			endDay: weekday,
			endTime: timeString,
			weeksLater: z.union([z.literal(0), z.literal(1)])
		})
	]),
	window: timeWindowSchema.nullable(),
	days: z.array(weekday).max(7),
	bankHolidays: z.boolean(),
	schoolHolidaysOnly: z.boolean(),
	schoolAuthority: z.string().max(64).nullable(),
	customSchoolHolidays: dateRangeList,
	range: dateRangeSchema,
	dates: dateRangeList,
	timezone
});

/** Typed with or without "@", in any case; stored lowercase. */
const username = z
	.string()
	.max(64)
	.transform(normalizeUsername)
	.superRefine((v, ctx) => {
		const problem = usernameProblem(v);
		if (problem) ctx.addIssue({ code: 'custom', message: problem });
	});

export const updateMeSchema = z
	.object({
		name: trimmed(80),
		themeMode: z.enum(THEME_MODES),
		colorTheme: z.enum(COLOR_THEMES),
		country: z.string().length(2).toUpperCase().nullable(),
		subdivision: z.string().max(10).nullable(),
		schoolAuthority: z.string().max(64).nullable(),
		timezone,
		onboarded: z.boolean(),
		username
	})
	.partial();

export const createGroupSchema = z.object({
	name: trimmed(80),
	color: z.enum(TILE_COLORS).optional(),
	requirement: requirementSchema.optional()
});

/**
 * `archived` moves the group to (true) or out of (false) the archived section of everyone's groups list;
 * `autoArchive` archives it by itself once its latest event has passed.
 */
export const updateGroupSchema = createGroupSchema.partial().extend({ archived: z.boolean().optional(), autoArchive: z.boolean().optional() });

export const memberRoleSchema = z.object({ role: z.enum(['admin', 'member']) });

/** The name you go by in one group; null or blank goes back to your account name. */
export const memberNameSchema = z.object({
	displayName: z
		.string()
		.trim()
		.max(80)
		.nullable()
		.transform((v) => v || null)
});

/** Leaving: the owner names the admin who becomes owner. */
export const leaveGroupSchema = z.object({ newOwnerId: z.string().min(1).nullish() });

/**
 * Nothing: a link anyone can use. `emails` (or one `email`) and `usernames`: one invite each,
 * only that person can join.
 */
export const inviteSchema = z
	.object({
		email: z.email().optional(),
		emails: z.array(z.email()).max(20, 'Invite up to 20 people at a time').optional(),
		usernames: z.array(username).max(20, 'Invite up to 20 people at a time').optional()
	})
	.refine((i) => (i.emails?.length ?? 0) + (i.usernames?.length ?? 0) <= 20, { message: 'Invite up to 20 people at a time' });

const blockShape = {
	start: isoDateTime,
	end: isoDateTime,
	allDay: z.boolean().default(false),
	repeat: z.enum(REPEATS).default('none'),
	until: dateString.nullish().transform((v) => v ?? null),
	note: optionalText(120)
};
/** Compares instants, not strings: offsets and fractional seconds make string order differ from time order. */
const endsAfterStart = (b: { start?: string; end?: string }) =>
	!b.start || !b.end || Date.parse(b.start) < Date.parse(b.end);

export const createBlockSchema = z.object(blockShape).refine(endsAfterStart, { message: 'Ends before it starts' });
export const updateBlockSchema = z
	.object(blockShape)
	.partial()
	.refine(endsAfterStart, { message: 'Ends before it starts' });

export const caldavSchema = z.object({
	username: trimmed(255),
	appPassword: trimmed(255),
	serverUrl: z.url({ protocol: /^https$/, error: 'Calendar servers must use https' }).optional()
});

export const deviceSyncSchema = z.object({
	deviceId: trimmed(64),
	deviceName: trimmed(80),
	rangeStart: isoDateTime,
	rangeEnd: isoDateTime,
	// Uploads split into ranges: the end of the whole sync, and whether more ranges follow.
	coveredUntil: isoDateTime.optional(),
	more: z.boolean().optional(),
	calendars: z
		.array(
			z.object({
				externalId: trimmed(255),
				name: trimmed(120),
				color: z.string().max(32).nullable(),
				busy: z.boolean().optional()
			})
		)
		.max(200),
	busy: z
		// eventKey: the app's hash of the event's series id, so a series can be marked not busy.
		// title: the event's name (optional; stored encrypted, only ever shown to the owner).
		.array(
			z.object({
				calendarExternalId: trimmed(255),
				start: isoDateTime,
				end: isoDateTime,
				eventKey: z.string().max(64).optional(),
				title: z.string().max(500).nullable().optional()
			})
		)
		.max(20000)
});

export const calendarBusySchema = z.object({ busy: z.boolean() });

const hhmm = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Use HH:MM');

/** A member's preferred time for a group: specific dates, or repeating weekdays (optionally within a window). */
export const preferenceSchema = z.discriminatedUnion('kind', [
	z.object({ kind: z.literal('dates'), from: z.iso.date(), to: z.iso.date() }),
	z.object({
		kind: z.literal('weekly'),
		days: z.array(z.number().int().min(0).max(6)).max(7).default([]),
		window: z.object({ from: hhmm, to: hhmm }).nullable().default(null)
	})
]);

/** "Not busy" for a calendar event: that occurrence (start/end), or its whole series. */
export const busyIgnoreSchema = z.object({
	calendarId: trimmed(36),
	eventKey: z.string().max(64),
	start: isoDateTime,
	end: isoDateTime,
	series: z.boolean().default(false)
});

const eventShape = {
	title: trimmed(120),
	start: isoDateTime,
	end: isoDateTime,
	allDay: z.boolean().default(false),
	location: optionalText(160),
	note: optionalText(1000)
};

export const createProposalSchema = z
	.object({ ...eventShape, title: optionalText(120) })
	.refine(endsAfterStart, { message: 'Ends before it starts' });

export const respondSchema = z.object({ response: z.enum(['accepted', 'declined']) });
export const overrideSchema = z.object({ userId: z.string().min(1), response: z.enum(RESPONSE_KINDS) });

export const createEventSchema = z.object(eventShape).refine(endsAfterStart, { message: 'Ends before it starts' });
export const updateEventSchema = z.object(eventShape).partial().refine(endsAfterStart, { message: 'Ends before it starts' });

export const createMessageSchema = z.union([
	// Photos / files uploaded first (POST /groups/:id/attachments), with an optional caption.
	z.object({
		body: optionalText(4000),
		attachmentIds: z.array(z.string().min(1).max(36)).min(1).max(10),
		replyToId: z.string().min(1).max(36).nullish()
	}),
	z.object({ body: trimmed(4000), replyToId: z.string().min(1).max(36).nullish() }),
	// A place: the sender's current location or one they searched for.
	z.object({
		location: z.object({
			lat: z.number().finite().min(-90).max(90),
			lng: z.number().finite().min(-180).max(180),
			name: optionalText(200),
			address: optionalText(300),
			accuracy: z.number().finite().min(0).max(100_000).nullish().transform((v) => v ?? null)
		}),
		replyToId: z.string().min(1).max(36).nullish()
	}),
	z.object({
		poll: z.object({
			question: trimmed(200),
			options: z.array(trimmed(100)).min(2).max(10),
			multi: z.boolean().default(false)
		})
	})
]);

// Empty only for a message with attachments (removes its caption).
export const editMessageSchema = z.object({ body: z.string().trim().max(4000) });

export const receiptsSchema = z.object({ upTo: z.string().min(1), state: z.enum(['delivered', 'seen']) });
export const voteSchema = z.object({ optionIds: z.array(z.string().min(1)).max(10) });
export const readNotificationsSchema = z.object({ ids: z.array(z.string()).max(500).optional() });
export const deviceSchema = z.object({
	platform: z.enum(['ios', 'android']),
	token: trimmed(512),
	name: optionalText(80)
});

export const rangeQuerySchema = z.object({ from: dateString, to: dateString });

export const createTemplateSchema = z.object({ name: trimmed(60), requirement: requirementSchema });
export const updateTemplateSchema = z.object({ name: trimmed(60).optional(), requirement: requirementSchema.optional() });

export const linkProviderSchema = z.enum(['apple', 'google']);

/** POST /me/accounts/link (native apps: an id token from Sign in with Apple / Google). */
export const linkAccountSchema = z.object({
	provider: linkProviderSchema,
	idToken: z.string().min(10).max(8192),
	nonce: z.string().max(512).optional(),
	/** The sign-in has its own account and the person confirmed merging it into this one. */
	merge: z.boolean().optional()
});

/** POST /me/accounts/merge-intent (web) */
export const mergeIntentSchema = z.object({ provider: linkProviderSchema });
