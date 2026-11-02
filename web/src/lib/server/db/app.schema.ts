import { relations } from 'drizzle-orm';
import {
	mysqlTable,
	varchar,
	text,
	timestamp,
	boolean,
	int,
	json,
	date,
	mysqlEnum,
	index,
	uniqueIndex,
	primaryKey
} from 'drizzle-orm/mysql-core';
import { user } from './auth.schema';
import {
	CALENDAR_PROVIDERS,
	NOTIFICATION_KINDS,
	REPEATS,
	RESPONSE_KINDS,
	ROLES,
	type Requirement
} from '../../types';

const id = () => varchar('id', { length: 36 }).primaryKey();
const userRef = (name = 'user_id') =>
	varchar(name, { length: 36 })
		.notNull()
		.references(() => user.id, { onDelete: 'cascade' });
const instant = (name: string) => timestamp(name, { fsp: 3 });
const createdAt = () => instant('created_at').defaultNow().notNull();
const updatedAt = () =>
	instant('updated_at')
		.defaultNow()
		.$onUpdate(() => new Date())
		.notNull();

// ─── Calendars ────────────────────────────────────────────────────────────
// One row per linked account (Google, Microsoft, CalDAV) or per device that
// uploads busy time from on-device calendars.
export const calendarConnections = mysqlTable(
	'calendar_connections',
	{
		id: id(),
		userId: userRef(),
		provider: mysqlEnum('provider', CALENDAR_PROVIDERS).notNull(),
		label: varchar('label', { length: 255 }).notNull(),
		// Device installs are identified by a stable id so re-syncs update in place.
		deviceId: varchar('device_id', { length: 64 }),
		// AES-GCM encrypted JSON (OAuth tokens or CalDAV credentials). Never sent to clients.
		credentials: text('credentials'),
		status: mysqlEnum('status', ['ok', 'error', 'reauth_required']).default('ok').notNull(),
		lastError: varchar('last_error', { length: 255 }),
		lastSyncedAt: instant('last_synced_at'),
		createdAt: createdAt(),
		updatedAt: updatedAt()
	},
	(t) => [
		index('calendar_connections_user_idx').on(t.userId),
		uniqueIndex('calendar_connections_device_uq').on(t.userId, t.deviceId)
	]
);

export const calendars = mysqlTable(
	'calendars',
	{
		id: id(),
		connectionId: varchar('connection_id', { length: 36 })
			.notNull()
			.references(() => calendarConnections.id, { onDelete: 'cascade' }),
		externalId: varchar('external_id', { length: 255 }).notNull(),
		name: varchar('name', { length: 160 }).notNull(),
		color: varchar('color', { length: 32 }),
		// Whether events in this calendar make the user busy.
		busy: boolean('busy').default(true).notNull()
	},
	(t) => [uniqueIndex('calendars_external_uq').on(t.connectionId, t.externalId)]
);

/** Busy time pulled from a calendar. Titles are never stored. */
export const busyIntervals = mysqlTable(
	'busy_intervals',
	{
		id: id(),
		userId: userRef(),
		calendarId: varchar('calendar_id', { length: 36 })
			.notNull()
			.references(() => calendars.id, { onDelete: 'cascade' }),
		start: instant('start').notNull(),
		end: instant('end').notNull()
	},
	(t) => [
		index('busy_intervals_user_range_idx').on(t.userId, t.start, t.end),
		index('busy_intervals_calendar_idx').on(t.calendarId, t.start)
	]
);

/** Busy time a user marked by hand ("My time"). */
export const manualBlocks = mysqlTable(
	'manual_blocks',
	{
		id: id(),
		userId: userRef(),
		start: instant('start').notNull(),
		end: instant('end').notNull(),
		allDay: boolean('all_day').default(false).notNull(),
		repeat: mysqlEnum('repeat', REPEATS).default('none').notNull(),
		until: date('until', { mode: 'string' }),
		note: varchar('note', { length: 120 }),
		createdAt: createdAt(),
		updatedAt: updatedAt()
	},
	(t) => [index('manual_blocks_user_idx').on(t.userId, t.start)]
);

// ─── Groups ───────────────────────────────────────────────────────────────
export const groups = mysqlTable('groups', {
	id: id(),
	name: varchar('name', { length: 80 }).notNull(),
	color: varchar('color', { length: 8 }).default('t1').notNull(),
	requirement: json('requirement').$type<Requirement>().notNull(),
	createdBy: varchar('created_by', { length: 36 }).references(() => user.id, { onDelete: 'set null' }),
	lastNudgedAt: instant('last_nudged_at'),
	createdAt: createdAt(),
	updatedAt: updatedAt()
});

export const groupMembers = mysqlTable(
	'group_members',
	{
		groupId: varchar('group_id', { length: 36 })
			.notNull()
			.references(() => groups.id, { onDelete: 'cascade' }),
		userId: userRef(),
		role: mysqlEnum('role', ROLES).default('member').notNull(),
		joinedAt: instant('joined_at').defaultNow().notNull()
	},
	(t) => [primaryKey({ columns: [t.groupId, t.userId] }), index('group_members_user_idx').on(t.userId)]
);

export const groupInvites = mysqlTable(
	'group_invites',
	{
		id: id(),
		groupId: varchar('group_id', { length: 36 })
			.notNull()
			.references(() => groups.id, { onDelete: 'cascade' }),
		token: varchar('token', { length: 64 }).notNull().unique(),
		email: varchar('email', { length: 255 }),
		createdBy: userRef('created_by'),
		expiresAt: instant('expires_at').notNull(),
		createdAt: createdAt()
	},
	(t) => [index('group_invites_group_idx').on(t.groupId)]
);

// ─── Proposals & events ───────────────────────────────────────────────────
export const proposals = mysqlTable(
	'proposals',
	{
		id: id(),
		groupId: varchar('group_id', { length: 36 })
			.notNull()
			.references(() => groups.id, { onDelete: 'cascade' }),
		createdBy: userRef('created_by'),
		title: varchar('title', { length: 120 }),
		location: varchar('location', { length: 160 }),
		note: text('note'),
		start: instant('start').notNull(),
		end: instant('end').notNull(),
		allDay: boolean('all_day').default(false).notNull(),
		status: mysqlEnum('status', ['open', 'confirmed', 'cancelled']).default('open').notNull(),
		eventId: varchar('event_id', { length: 36 }),
		createdAt: createdAt()
	},
	(t) => [index('proposals_group_idx').on(t.groupId, t.status)]
);

export const proposalResponses = mysqlTable(
	'proposal_responses',
	{
		proposalId: varchar('proposal_id', { length: 36 })
			.notNull()
			.references(() => proposals.id, { onDelete: 'cascade' }),
		userId: userRef(),
		response: mysqlEnum('response', RESPONSE_KINDS).default('pending').notNull(),
		// Set when an admin overrode this member's response.
		setBy: varchar('set_by', { length: 36 }),
		updatedAt: updatedAt()
	},
	(t) => [primaryKey({ columns: [t.proposalId, t.userId] })]
);

export const events = mysqlTable(
	'events',
	{
		id: id(),
		groupId: varchar('group_id', { length: 36 })
			.notNull()
			.references(() => groups.id, { onDelete: 'cascade' }),
		proposalId: varchar('proposal_id', { length: 36 }),
		createdBy: userRef('created_by'),
		title: varchar('title', { length: 120 }).notNull(),
		location: varchar('location', { length: 160 }),
		note: text('note'),
		start: instant('start').notNull(),
		end: instant('end').notNull(),
		allDay: boolean('all_day').default(false).notNull(),
		createdAt: createdAt(),
		updatedAt: updatedAt()
	},
	(t) => [index('events_group_idx').on(t.groupId, t.start)]
);

// ─── Chat ─────────────────────────────────────────────────────────────────
export const polls = mysqlTable('polls', {
	id: id(),
	groupId: varchar('group_id', { length: 36 })
		.notNull()
		.references(() => groups.id, { onDelete: 'cascade' }),
	createdBy: userRef('created_by'),
	question: varchar('question', { length: 200 }).notNull(),
	multi: boolean('multi').default(false).notNull(),
	closedAt: instant('closed_at'),
	createdAt: createdAt()
});

export const pollOptions = mysqlTable(
	'poll_options',
	{
		id: id(),
		pollId: varchar('poll_id', { length: 36 })
			.notNull()
			.references(() => polls.id, { onDelete: 'cascade' }),
		label: varchar('label', { length: 100 }).notNull(),
		position: int('position').notNull()
	},
	(t) => [index('poll_options_poll_idx').on(t.pollId)]
);

export const pollVotes = mysqlTable(
	'poll_votes',
	{
		optionId: varchar('option_id', { length: 36 })
			.notNull()
			.references(() => pollOptions.id, { onDelete: 'cascade' }),
		pollId: varchar('poll_id', { length: 36 })
			.notNull()
			.references(() => polls.id, { onDelete: 'cascade' }),
		userId: userRef(),
		createdAt: createdAt()
	},
	(t) => [primaryKey({ columns: [t.optionId, t.userId] }), index('poll_votes_poll_idx').on(t.pollId)]
);

export const messages = mysqlTable(
	'messages',
	{
		id: id(),
		groupId: varchar('group_id', { length: 36 })
			.notNull()
			.references(() => groups.id, { onDelete: 'cascade' }),
		// Null for system messages; kept (set null) if the author deletes their account.
		authorId: varchar('author_id', { length: 36 }).references(() => user.id, { onDelete: 'set null' }),
		kind: mysqlEnum('kind', ['text', 'poll', 'proposal', 'event', 'system']).notNull(),
		body: text('body'),
		pollId: varchar('poll_id', { length: 36 }),
		proposalId: varchar('proposal_id', { length: 36 }),
		eventId: varchar('event_id', { length: 36 }),
		// Monotonic per insert so pagination and "read up to" never tie.
		seq: int('seq').autoincrement().notNull().unique(),
		createdAt: createdAt()
	},
	(t) => [index('messages_group_seq_idx').on(t.groupId, t.seq)]
);

/** Per-recipient delivery and read state for each message. */
export const messageReceipts = mysqlTable(
	'message_receipts',
	{
		messageId: varchar('message_id', { length: 36 })
			.notNull()
			.references(() => messages.id, { onDelete: 'cascade' }),
		userId: userRef(),
		deliveredAt: instant('delivered_at').notNull(),
		seenAt: instant('seen_at')
	},
	(t) => [primaryKey({ columns: [t.messageId, t.userId] }), index('message_receipts_user_idx').on(t.userId)]
);

// ─── Notifications & push ─────────────────────────────────────────────────
export const notifications = mysqlTable(
	'notifications',
	{
		id: id(),
		userId: userRef(),
		groupId: varchar('group_id', { length: 36 }).references(() => groups.id, { onDelete: 'cascade' }),
		kind: mysqlEnum('kind', NOTIFICATION_KINDS).notNull(),
		title: varchar('title', { length: 160 }).notNull(),
		body: varchar('body', { length: 400 }).notNull(),
		data: json('data').$type<Record<string, string>>().notNull(),
		readAt: instant('read_at'),
		createdAt: createdAt()
	},
	(t) => [index('notifications_user_idx').on(t.userId, t.createdAt)]
);

export const devices = mysqlTable(
	'devices',
	{
		id: id(),
		userId: userRef(),
		platform: mysqlEnum('platform', ['ios', 'android']).notNull(),
		token: varchar('token', { length: 512 }).notNull().unique(),
		name: varchar('name', { length: 80 }),
		updatedAt: updatedAt()
	},
	(t) => [index('devices_user_idx').on(t.userId)]
);

// ─── Reference data ───────────────────────────────────────────────────────
/** School holiday dates per local authority, imported with scripts/import-school-holidays.ts. */
export const schoolHolidays = mysqlTable(
	'school_holidays',
	{
		id: id(),
		country: varchar('country', { length: 2 }).notNull(),
		authority: varchar('authority', { length: 64 }).notNull(),
		authorityName: varchar('authority_name', { length: 160 }).notNull(),
		name: varchar('name', { length: 120 }).notNull(),
		start: date('start', { mode: 'string' }).notNull(),
		end: date('end', { mode: 'string' }).notNull()
	},
	(t) => [index('school_holidays_authority_idx').on(t.country, t.authority, t.start)]
);

/** Cached public holiday lists from Nager.Date, one row per country and year. */
export const holidayCache = mysqlTable(
	'holiday_cache',
	{
		country: varchar('country', { length: 2 }).notNull(),
		year: int('year').notNull(),
		holidays: json('holidays').$type<{ date: string; name: string; subdivisions: string[] | null }[]>().notNull(),
		fetchedAt: instant('fetched_at').defaultNow().notNull()
	},
	(t) => [primaryKey({ columns: [t.country, t.year] })]
);

// ─── Relations ────────────────────────────────────────────────────────────
export const calendarConnectionRelations = relations(calendarConnections, ({ many }) => ({
	calendars: many(calendars)
}));

export const calendarRelations = relations(calendars, ({ one }) => ({
	connection: one(calendarConnections, { fields: [calendars.connectionId], references: [calendarConnections.id] })
}));

// ─── Timing templates ─────────────────────────────────────────────────────

/** A user's saved group timings ("Weekends in school holidays") to reuse for new groups. */
export const requirementTemplates = mysqlTable(
	'requirement_templates',
	{
		id: id(),
		userId: userRef(),
		name: varchar('name', { length: 60 }).notNull(),
		requirement: json('requirement').$type<Requirement>().notNull(),
		createdAt: createdAt(),
		updatedAt: updatedAt()
	},
	(t) => [index('requirement_templates_user_idx').on(t.userId)]
);

// ─── Plans ────────────────────────────────────────────────────────────────

/**
 * Pro entitlement, mirrored from RevenueCat (App Store, Play Store and web
 * purchases through Stripe). No row, or `proUntil` in the past, means free.
 */
export const userPlans = mysqlTable('user_plans', {
	userId: varchar('user_id', { length: 36 })
		.primaryKey()
		.references(() => user.id, { onDelete: 'cascade' }),
	/** When Pro runs out; null with `lifetime` for one-off purchases. */
	proUntil: instant('pro_until'),
	lifetime: boolean('lifetime').default(false).notNull(),
	/** app_store, play_store, stripe, rc_billing, promotional … as RevenueCat reports it. */
	store: varchar('store', { length: 32 }),
	productId: varchar('product_id', { length: 128 }),
	/** The subscription won't renew (cancelled), but stays active until proUntil. */
	willRenew: boolean('will_renew').default(true).notNull(),
	updatedAt: updatedAt()
});

export const groupRelations = relations(groups, ({ many }) => ({
	members: many(groupMembers)
}));

export const groupMemberRelations = relations(groupMembers, ({ one }) => ({
	group: one(groups, { fields: [groupMembers.groupId], references: [groups.id] }),
	user: one(user, { fields: [groupMembers.userId], references: [user.id] })
}));

export const proposalRelations = relations(proposals, ({ many }) => ({
	responses: many(proposalResponses)
}));

export const proposalResponseRelations = relations(proposalResponses, ({ one }) => ({
	proposal: one(proposals, { fields: [proposalResponses.proposalId], references: [proposals.id] })
}));

export const pollRelations = relations(polls, ({ many }) => ({
	options: many(pollOptions)
}));

export const pollOptionRelations = relations(pollOptions, ({ one }) => ({
	poll: one(polls, { fields: [pollOptions.pollId], references: [polls.id] })
}));
