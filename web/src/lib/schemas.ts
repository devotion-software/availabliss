/**
 * Request validation shared by the API handlers and the web forms.
 * Each schema mirrors a request body in docs/API.md.
 */
import { z } from 'zod';
import { COLOR_THEMES, REPEATS, RESPONSE_KINDS, THEME_MODES, TILE_COLORS, type Weekday } from './types';

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;

export const dateString = z.string().regex(DATE, 'Expected YYYY-MM-DD');
export const timeString = z.string().regex(TIME, 'Expected HH:MM');
export const isoDateTime = z.iso.datetime({ offset: true });
const weekday = z.int().min(0).max(6).transform((d) => d as Weekday);
const trimmed = (max: number) => z.string().trim().min(1).max(max);
const optionalText = (max: number) => z.string().trim().max(max).nullish().transform((v) => v || null);

export const dateRangeSchema = z
	.object({ from: dateString, to: dateString })
	.refine((r) => r.from <= r.to, { message: 'Range ends before it starts' });

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
	customSchoolHolidays: z.array(dateRangeSchema).max(50),
	range: dateRangeSchema,
	dates: z.array(dateRangeSchema).max(50),
	timezone: z.string().min(1).max(64)
});

export const updateMeSchema = z
	.object({
		name: trimmed(80),
		themeMode: z.enum(THEME_MODES),
		colorTheme: z.enum(COLOR_THEMES),
		country: z.string().length(2).toUpperCase().nullable(),
		subdivision: z.string().max(10).nullable(),
		schoolAuthority: z.string().max(64).nullable(),
		timezone: z.string().min(1).max(64),
		onboarded: z.boolean()
	})
	.partial();

export const createGroupSchema = z.object({
	name: trimmed(80),
	color: z.enum(TILE_COLORS).optional(),
	requirement: requirementSchema.optional()
});

export const updateGroupSchema = createGroupSchema.partial();

export const memberRoleSchema = z.object({ role: z.enum(['admin', 'member']) });

export const inviteSchema = z.object({ email: z.email().optional() });

const blockShape = {
	start: isoDateTime,
	end: isoDateTime,
	allDay: z.boolean().default(false),
	repeat: z.enum(REPEATS).default('none'),
	until: dateString.nullish().transform((v) => v ?? null),
	note: optionalText(120)
};
const endsAfterStart = (b: { start?: string; end?: string }) => !b.start || !b.end || b.start < b.end;

export const createBlockSchema = z.object(blockShape).refine(endsAfterStart, { message: 'Ends before it starts' });
export const updateBlockSchema = z
	.object(blockShape)
	.partial()
	.refine(endsAfterStart, { message: 'Ends before it starts' });

export const caldavSchema = z.object({
	username: trimmed(255),
	appPassword: trimmed(255),
	serverUrl: z.url().optional()
});

export const deviceSyncSchema = z.object({
	deviceId: trimmed(64),
	deviceName: trimmed(80),
	rangeStart: isoDateTime,
	rangeEnd: isoDateTime,
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
		.array(z.object({ calendarExternalId: trimmed(255), start: isoDateTime, end: isoDateTime }))
		.max(20000)
});

export const calendarBusySchema = z.object({ busy: z.boolean() });

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
	z.object({ body: trimmed(4000) }),
	z.object({
		poll: z.object({
			question: trimmed(200),
			options: z.array(trimmed(100)).min(2).max(10),
			multi: z.boolean().default(false)
		})
	})
]);

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
