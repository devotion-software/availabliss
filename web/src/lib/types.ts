/**
 * Shapes returned by the /api/v1 JSON API. Shared by the server (which builds
 * them) and the web client (which renders them). Keep in sync with docs/API.md.
 */

export const ROLES = ['owner', 'admin', 'member'] as const;
export type Role = (typeof ROLES)[number];

export const THEME_MODES = ['light', 'dark', 'system'] as const;
export type ThemeMode = (typeof THEME_MODES)[number];

export const COLOR_THEMES = ['dawn', 'lagoon', 'sunset', 'orchid', 'grove'] as const;
export type ColorTheme = (typeof COLOR_THEMES)[number];

export const TILE_COLORS = ['t1', 't2', 't3', 't4', 't5'] as const;
export type TileColor = (typeof TILE_COLORS)[number];

export const CALENDAR_PROVIDERS = ['google', 'microsoft', 'caldav', 'device'] as const;
export type CalendarProvider = (typeof CALENDAR_PROVIDERS)[number];

/**
 * How a block repeats. `monthly` is the same date each month (skipped in months without it);
 * `monthlyWeekday` the same weekday of the month (e.g. the 2nd Tuesday, or the last one if it
 * started in a month's last seven days); `yearly` the same date each year.
 */
export const REPEATS = ['none', 'daily', 'weekdays', 'weekly', 'fortnightly', 'monthly', 'monthlyWeekday', 'yearly'] as const;
export type Repeat = (typeof REPEATS)[number];

/**
 * A reply to a proposal. `maybe` is a soft yes ("I can make it if I must"): counted apart from
 * `accepted`, and it doesn't count towards everyone being in, so a proposal only confirms itself
 * when every reply is a firm yes (the proposer or an admin can still confirm anyway).
 */
export const RESPONSE_KINDS = ['pending', 'accepted', 'maybe', 'declined'] as const;
export type ResponseKind = (typeof RESPONSE_KINDS)[number];

/** Weekday index, 0 = Monday … 6 = Sunday. */
export type Weekday = 0 | 1 | 2 | 3 | 4 | 5 | 6;

export interface DateRange {
	/** Inclusive YYYY-MM-DD. */
	from: string;
	/** Inclusive YYYY-MM-DD. */
	to: string;
}

export interface TimeWindow {
	/** HH:MM */
	from: string;
	/** HH:MM */
	to: string;
}

export type RequirementLength =
	| { type: 'duration'; minutes: number }
	| { type: 'days'; count: number }
	| {
			type: 'span';
			startDay: Weekday;
			startTime: string;
			endDay: Weekday;
			endTime: string;
			weeksLater: 0 | 1;
	  };

export interface Requirement {
	length: RequirementLength;
	window: TimeWindow | null;
	days: Weekday[];
	bankHolidays: boolean;
	schoolHolidaysOnly: boolean;
	schoolAuthority: string | null;
	customSchoolHolidays: DateRange[];
	range: DateRange;
	dates: DateRange[];
	timezone: string;
	/**
	 * "Most of us are free": a time works when at least this many of the required members (synced,
	 * not optional) are free. Null or absent: everyone.
	 */
	quorum?: number | null;
	/** Members who never stop a time from working (they're shown as missing when busy). */
	optionalMemberIds?: string[];
}

export interface User {
	id: string;
	name: string;
	email: string;
	/** Lowercase, without the "@". */
	username: string | null;
	image: string | null;
	themeMode: ThemeMode;
	colorTheme: ColorTheme;
	country: string | null;
	subdivision: string | null;
	schoolAuthority: string | null;
	timezone: string;
	onboarded: boolean;
	/** Minutes counted as busy before and after each busy time when finding free slots (0 = none). */
	busyBufferMinutes: BusyBufferMinutes;
	/** Pushes before a confirmed plan you're going to: the day before, an hour before, both or none. */
	eventReminders: EventReminders;
}

/** Kinds of push you can turn off, each covering some notification kinds (see NOTIFICATION_CATEGORY_KINDS). */
export const NOTIFICATION_CATEGORIES = ['chat', 'proposals', 'plans', 'reminders', 'nudges'] as const;
export type NotificationCategory = (typeof NOTIFICATION_CATEGORIES)[number];

export interface NotificationSettings {
	/** Categories whose pushes (and emails) are off. Activity still lists everything. */
	off: NotificationCategory[];
	/** No pushes between these local times (in your time zone), e.g. 22:00–07:00; null for none. */
	quietHours: { from: string; to: string } | null;
	/** Groups you've muted: no pushes from them until `until` (null = until you unmute). */
	mutes: { groupId: string; until: string | null }[];
}

export const EVENT_REMINDERS = ['both', 'day', 'hour', 'off'] as const;
export type EventReminders = (typeof EVENT_REMINDERS)[number];

/** The buffers on offer around busy time ("None", 15 min, 30 min, 1 hour, 2 hours). */
export const BUSY_BUFFER_MINUTES = [0, 15, 30, 60, 120] as const;
export type BusyBufferMinutes = (typeof BUSY_BUFFER_MINUTES)[number];

export interface Member {
	userId: string;
	/** The name they go by in this group: `nameInGroup` when set, else their account name. */
	name: string;
	/** The name they chose for this group, or null to use their account name. */
	nameInGroup: string | null;
	image: string | null;
	role: Role;
	synced: boolean;
	joinedAt: string;
}

/** What one slot is called: "2 weekends work", "3 evenings work". */
export type SlotNoun = 'weekend' | 'evening' | 'day' | 'week' | 'trip' | 'slot';

export interface MemberPreview {
	userId: string;
	name: string;
	image: string | null;
}

export type GroupStatus =
	| { kind: 'matches'; count: number; noun: SlotNoun }
	| { kind: 'needs_reply'; proposalId: string }
	| { kind: 'confirmed'; eventId: string; start: string }
	| { kind: 'searching' };

export interface GroupSummary {
	id: string;
	name: string;
	color: TileColor;
	monogram: string;
	/** The group's photo, shown instead of the monogram tile; null for none. */
	image: string | null;
	role: Role;
	/** Archived by an admin: listed in the groups list's collapsed Archived section. */
	archived: boolean;
	memberCount: number;
	syncedCount: number;
	requirementSummary: string;
	status: GroupStatus;
	unreadCount: number;
	/** First few members, for avatar stacks. */
	memberPreview: MemberPreview[];
}

export interface Group extends Omit<GroupSummary, 'status' | 'unreadCount' | 'memberPreview'> {
	requirement: Requirement;
	members: Member[];
	/** Archived automatically once its latest event has passed and nothing else is planned (admins choose). */
	autoArchive: boolean;
	createdAt: string;
}

export interface BusyInterval {
	start: string;
	end: string;
	source: 'calendar' | 'manual';
	label: string | null;
	calendarId: string | null;
	blockId: string | null;
	/** Calendar busy: the event's name, when the calendar gives one. Only ever sent to its owner. */
	title?: string | null;
	/** Calendar busy: the event's opaque key ('' when the calendar gives no ids). */
	eventKey?: string;
	/** Calendar busy: the event repeats (so "not busy" can cover every occurrence). */
	recurring?: boolean;
	/** Set when you've marked this event not busy: the id to undo it with. It doesn't count as busy. */
	ignoreId?: string | null;
}

export interface CalendarConnection {
	id: string;
	provider: CalendarProvider;
	label: string;
	status: 'ok' | 'error' | 'reauth_required';
	lastSyncedAt: string | null;
	calendars: { id: string; name: string; color: string | null; busy: boolean }[];
	/** Phone calendars: how far ahead the app should read (ISO instant). Null for server calendars. */
	syncUntil: string | null;
}

export interface ManualBlock {
	id: string;
	start: string;
	end: string;
	allDay: boolean;
	repeat: Repeat;
	until: string | null;
	note: string | null;
}

export type DayStatus = 'hidden' | 'busy' | 'free' | 'match';
export type SlotPosition = 'start' | 'middle' | 'end' | 'single';

export interface AvailabilityDay {
	date: string;
	status: DayStatus;
	busyMemberIds: string[];
	slotPosition: SlotPosition | null;
	holiday: string | null;
	/** Members whose preferences this day's time that works fits (only on `match` days). */
	preferredBy?: string[];
	/** A `match` day whose best time only meets the group's quorum (someone's missing). */
	partial?: boolean;
}

export interface Slot {
	start: string;
	end: string;
	label: string;
	/** Members whose preferences this slot fits. */
	preferredBy?: string[];
	/** Pro: the "best date" score (higher is better), weighing preferences and who's compromised before. */
	score?: number;
	/** Pro: people this time suits who've compromised in the group's recent plans. */
	fairFor?: string[];
	/** Synced members busy at this time (with a quorum or optional members); empty when everyone's free. */
	missingMemberIds?: string[];
}

/** A member's preferred time for a group. A guideline: it never blocks a date. */
export interface GroupPreference {
	id: string;
	userId: string;
	/** `dates`: from–to (inclusive dates). `weekly`: the weekdays (empty = any), optionally within a window. */
	kind: 'dates' | 'weekly';
	from: string | null;
	to: string | null;
	days: Weekday[];
	window: { from: string; to: string } | null;
	/** "Weekends after 16:00", "12 – 14 Dec". */
	label: string;
}

export interface Availability {
	from: string;
	to: string;
	visibleWeekdays: Weekday[];
	days: AvailabilityDay[];
	slots: Slot[];
	syncedCount: number;
	memberCount: number;
	pendingMemberIds: string[];
	/** Synced members whose calendars are only checked up to `until` (ISO instant), before the end of this range. */
	checkedUntil: { userId: string; until: string }[];
}

export interface Interval {
	start: string;
	end: string;
}

export interface DayAvailability {
	date: string;
	window: Interval | null;
	members: { userId: string; pending: boolean; busy: (Interval & { manual: boolean })[] }[];
	everyoneFree: Interval[];
	/** With a quorum or optional members: when enough of the group is free (includes everyoneFree). */
	mostFree?: Interval[];
}

export interface ProposalResponse {
	userId: string;
	response: ResponseKind;
	setBy: string | null;
	updatedAt: string;
	/** Confirmed proposals: said they can't make it after it was confirmed, not yet reviewed (see `needsReview`). */
	droppedOut: boolean;
}

export interface Proposal {
	id: string;
	groupId: string;
	title: string | null;
	location: string | null;
	note: string | null;
	start: string;
	end: string;
	allDay: boolean;
	status: 'open' | 'confirmed' | 'cancelled';
	createdBy: string;
	createdAt: string;
	eventId: string | null;
	/**
	 * Confirmed proposals: who confirmed it before everyone had accepted ("Confirm anyway"),
	 * null when it confirmed itself because everyone accepted.
	 */
	confirmedBy: string | null;
	responses: ProposalResponse[];
	/**
	 * Confirmed proposals: someone who was coming can't make it any more, and the proposer or an
	 * admin hasn't yet chosen to keep the plan (`POST /proposals/:id/keep`) or cancel it.
	 */
	needsReview: boolean;
	/** Open proposals only: members whose calendars or blocks say they're busy at this time. */
	busyMemberIds: string[];
}

export interface GroupEvent {
	id: string;
	groupId: string;
	proposalId: string | null;
	title: string;
	location: string | null;
	note: string | null;
	start: string;
	end: string;
	allDay: boolean;
	/** A cover photo; null for none. */
	image: string | null;
	createdBy: string;
	createdAt: string;
}

export interface Poll {
	id: string;
	question: string;
	multi: boolean;
	closed: boolean;
	options: { id: string; label: string; voterIds: string[] }[];
}

export type MessageKind = 'text' | 'poll' | 'proposal' | 'event' | 'system';

export interface Message {
	id: string;
	groupId: string;
	authorId: string | null;
	kind: MessageKind;
	body: string | null;
	poll: Poll | null;
	proposalId: string | null;
	eventId: string | null;
	createdAt: string;
	/** When the author last edited the text, or null. */
	editedAt: string | null;
	/** Deleted by its author: shown as "This message was deleted" (body is null). */
	deleted: boolean;
	receipt: { delivered: number; seen: number; recipients: number; seenBy: string[] } | null;
	/** A compact preview of the message this one replies to, or null. */
	replyTo: MessageReply | null;
	/** Photos, GIFs and files sent with it, in order (empty for most messages, and once deleted). */
	attachments: Attachment[];
	/**
	 * With attachments, `body` is the caption or (with none) a short summary such as "📷 Photo"
	 * for older apps; `caption` is just the caption, or null. Null for messages without attachments.
	 */
	caption: string | null;
	/** A place shared in the chat (a one-off pin). `body` is then a fallback for older apps; null once deleted. */
	location: MessageLocation | null;
}

/** A place shared in a chat message: where it is and, if it's a named place, what it's called. */
export interface MessageLocation {
	lat: number;
	lng: number;
	/** A place's name ("Dishoom Shoreditch"), or null for "my location". */
	name: string | null;
	address: string | null;
	/** How precise a device's position was, in metres; null for a searched place. */
	accuracy: number | null;
}

export const ATTACHMENT_KINDS = ['image', 'gif', 'file', 'pass'] as const;
export type AttachmentKind = (typeof ATTACHMENT_KINDS)[number];

/** A photo, GIF or file in a chat message. URLs are signed and expire after about a week: refetch the message for fresh ones. */
export interface Attachment {
	id: string;
	kind: AttachmentKind;
	name: string;
	mime: string;
	/** Bytes of the stored file. */
	size: number;
	/** Pixels, for images and GIFs (null for files). */
	width: number | null;
	height: number | null;
	url: string;
	/** A smaller JPEG/PNG preview for photos, or a Wallet pass's logo; null for GIFs (show `url`, animated) and files. */
	thumbnailUrl: string | null;
	/** For `kind: 'pass'` (an Apple Wallet .pkpass / .pkpasses): what to show on its card. Null otherwise. */
	pass: WalletPass | null;
}

export type WalletPassStyle = 'boardingPass' | 'eventTicket' | 'coupon' | 'storeCard' | 'generic';

export interface WalletPassField {
	label: string | null;
	/** The text, or with `date` an ISO 8601 UTC date-time to show in the reader's own format. */
	value: string;
	date: boolean;
}

/** Details read from a Wallet pass's pass.json (English text where it is localised). */
export interface WalletPass {
	style: WalletPassStyle;
	organizationName: string;
	description: string;
	logoText: string | null;
	/** With serialNumber, identifies the pass (e.g. to tell whether it is already in Wallet). */
	passTypeIdentifier: string;
	serialNumber: string;
	/** When it is for (the event, the flight…), ISO 8601 UTC, or null. */
	relevantDate: string | null;
	expirationDate: string | null;
	voided: boolean;
	eventName: string | null;
	venue: string | null;
	/** Up to four of its fields, most important first. */
	fields: WalletPassField[];
	/** "#rrggbb", or null for Wallet's default. */
	backgroundColor: string | null;
	foregroundColor: string | null;
	labelColor: string | null;
	/** How many passes the file holds (a .pkpasses bundle; details are the first one's). */
	count: number;
}

/** A message's thread: the first message of its chain of replies, then every reply under it, oldest first. */
export interface MessageThread {
	rootId: string;
	messages: Message[];
	/** More replies than the server returns (the earliest are kept). */
	truncated: boolean;
}

/** The quoted message shown above a reply, as it is now (edits and deletion included). */
export interface MessageReply {
	id: string;
	authorId: string | null;
	authorName: string | null;
	kind: MessageKind;
	/** A short one-line snippet: the text, or a poll's question. Null once deleted. */
	text: string | null;
	deleted: boolean;
}

export interface MessageInfo {
	messageId: string;
	sentAt: string;
	seen: { userId: string; at: string }[];
	delivered: { userId: string; at: string }[];
	pending: string[];
}

export const NOTIFICATION_KINDS = [
	'proposal.created',
	'proposal.confirmed',
	/** A confirmed plan changed: someone can't make it any more, or it was cancelled. */
	'proposal.changed',
	'nudge',
	'invite.accepted',
	'invite.received',
	'message',
	'event.created',
	/** Your role in a group changed: made an admin, no longer an admin, or handed ownership. `data.role` is the new one. */
	'role.changed',
	/**
	 * A reminder: a confirmed plan is tomorrow or in an hour (`data.eventId`), or a proposal you
	 * haven't replied to is within a day (`data.proposalId`). `data.reminder` is 'day', 'hour' or 'reply'.
	 */
	'reminder'
] as const;
export type NotificationKind = (typeof NOTIFICATION_KINDS)[number];

export interface AppNotification {
	id: string;
	groupId: string | null;
	kind: NotificationKind;
	title: string;
	body: string;
	data: Record<string, string>;
	readAt: string | null;
	createdAt: string;
}

export type LiveEventType =
	| 'message.created'
	| 'message.updated'
	| 'message.receipts'
	| 'poll.updated'
	| 'proposal.created'
	| 'proposal.updated'
	| 'proposal.confirmed'
	| 'event.changed'
	| 'group.updated'
	| 'member.changed'
	| 'availability.changed'
	| 'notification.created';

export interface LiveEvent {
	type: LiveEventType;
	groupId: string | null;
	payload: unknown;
}

/** An invite that can still be used (`GET /groups/:id/invites`). */
export interface GroupInvite {
	id: string;
	/** Who it was emailed to (only they can join with it), or null for a link anyone can use. */
	email: string | null;
	/** Who it was sent to by username (only they can join with it), or null. */
	username: string | null;
	/** The account name of the person it was sent to in Activity (by username or user id), or null. */
	name?: string | null;
	url: string;
	/** How many people can join with it (null: no limit). Always 1 for an invite for one person. */
	maxUses: number | null;
	/** How many people have joined with it so far. */
	uses: number;
	expiresAt: string;
	createdAt: string;
	invitedBy: string;
}

/** Someone found by username (GET /usernames/:username). */
export interface UserSummary {
	id: string;
	username: string;
	name: string;
	image: string | null;
}

export interface UsernameCheck {
	username: string;
	/** Why it can't be used as written, or null. */
	problem: string | null;
	/** Free to take (valid and nobody has it, or it's already yours). */
	available: boolean;
	/** Who has it, when someone does (you included). */
	user: UserSummary | null;
}

export interface ApiErrorBody {
	message: string;
	code: 'unauthorized' | 'forbidden' | 'not_found' | 'validation_error' | 'conflict' | 'upgrade_required' | 'rate_limited' | 'server_error';
	issues?: { path: string; message: string }[];
}

/** Sign-in providers that can be linked to an account. */
export type LinkProvider = 'apple' | 'google';

/** GET /me/accounts */
export interface LinkedAccounts {
	/** Apple / Google sign-ins on this account (email always works too). */
	linked: { provider: LinkProvider; linkedAt: string }[];
	/** Providers this server can link. */
	available: LinkProvider[];
}

/** The account a sign-in already belongs to, shown before merging it into yours. */
export interface MergeCandidate {
	name: string;
	email: string;
	/** How many groups it's in. */
	groups: number;
}

export type PlanTier = 'free' | 'pro';

/** GET /me/plan */
export interface Plan {
	tier: PlanTier;
	/** When Pro ends (or renews); null on free or lifetime. */
	proUntil: string | null;
	lifetime: boolean;
	willRenew: boolean;
	/** Where it was bought: app_store, play_store, stripe, rc_billing, promotional. */
	store: string | null;
	/** Groups you can own on this plan; null is unlimited. */
	groupLimit: number | null;
	groupsOwned: number;
	canCreateGroup: boolean;
	/** RevenueCat public SDK keys, null when billing isn't set up (hide purchase UI). */
	purchase: { ios: string | null; android: string | null; web: string | null; entitlement: string };
	/** The optional AI planner is set up on this server (it still needs Pro to use). */
	aiPlanner: { available: boolean; included: boolean };
}

/** A saved timing to reuse when creating groups. */
export interface RequirementTemplate {
	id: string;
	name: string;
	requirement: Requirement;
	createdAt: string;
}

/** POST /groups/:id/planner (Pro): a suggested time, ideas and a plan. */
export interface PlannerResult {
	best: { slot: Slot; why: string };
	alternatives: { slot: Slot; why: string }[];
	ideas: { title: string; description: string }[];
	checklist: string[];
	proposalTitle: string;
	proposalNote: string;
}
