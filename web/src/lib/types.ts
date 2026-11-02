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

export const REPEATS = ['none', 'weekly', 'fortnightly'] as const;
export type Repeat = (typeof REPEATS)[number];

export const RESPONSE_KINDS = ['pending', 'accepted', 'declined'] as const;
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
}

export interface User {
	id: string;
	name: string;
	email: string;
	image: string | null;
	themeMode: ThemeMode;
	colorTheme: ColorTheme;
	country: string | null;
	subdivision: string | null;
	schoolAuthority: string | null;
	timezone: string;
	onboarded: boolean;
}

export interface Member {
	userId: string;
	name: string;
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
	role: Role;
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
	createdAt: string;
}

export interface BusyInterval {
	start: string;
	end: string;
	source: 'calendar' | 'manual';
	label: string | null;
	calendarId: string | null;
	blockId: string | null;
}

export interface CalendarConnection {
	id: string;
	provider: CalendarProvider;
	label: string;
	status: 'ok' | 'error' | 'reauth_required';
	lastSyncedAt: string | null;
	calendars: { id: string; name: string; color: string | null; busy: boolean }[];
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
}

export interface Slot {
	start: string;
	end: string;
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
}

export interface ProposalResponse {
	userId: string;
	response: ResponseKind;
	setBy: string | null;
	updatedAt: string;
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
	responses: ProposalResponse[];
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
	receipt: { delivered: number; seen: number; recipients: number; seenBy: string[] } | null;
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
	'nudge',
	'invite.accepted',
	'message',
	'event.created'
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

export interface ApiErrorBody {
	message: string;
	code: 'unauthorized' | 'forbidden' | 'not_found' | 'validation_error' | 'conflict' | 'upgrade_required' | 'rate_limited' | 'server_error';
	issues?: { path: string; message: string }[];
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
}
