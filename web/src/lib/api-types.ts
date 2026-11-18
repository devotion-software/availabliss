/**
 * Response shapes of the /api/v1 JSON API that aren't in types.ts: mostly small results and
 * the extras some endpoints add to a shared type. Together with types.ts these become
 * `components.schemas` in docs/openapi.json (scripts/openapi.ts). api-types.check.ts checks
 * that the server functions really return these shapes, so they can't drift silently.
 */
import type { ApiErrorBody, AppNotification, Group, GroupEvent, GroupInvite, Message, MergeCandidate, ResponseKind, Slot } from './types';

/** GET /health */
export interface Health {
	ok: boolean;
	version: string;
	db: 'ok' | 'down';
	redis: 'ok' | 'down' | 'off';
}

/** POST /auth/native/exchange */
export interface NativeToken {
	/** A bearer token for `Authorization: Bearer <token>`. */
	token: string;
}

/** GET /me/calendar-feed, POST /me/calendar-feed/reset */
export interface CalendarFeed {
	/** https link (to copy, and for Google Calendar's "From URL"). */
	url: string;
	/** The same feed as webcal://, which Apple Calendar and Outlook subscribe to. */
	webcalUrl: string;
}

/** A plan from any of your groups, with your reply (null: added straight to the calendar, nobody was asked). */
export type MyPlan = GroupEvent & { groupName: string; myResponse: ResponseKind | null };

/** GET /me/plans */
export interface MyPlans {
	/** Upcoming ones you said yes or maybe to, haven't replied to, or that went straight on the calendar (soonest first). */
	going: MyPlan[];
	/** Upcoming ones you said you can't make. */
	notGoing: MyPlan[];
	/** The last year, newest first (at most 100). */
	past: MyPlan[];
}

/** An event in GET /me/upcoming. */
export type UpcomingEvent = GroupEvent & { groupName: string };

/** POST /me/accounts/link when it worked. */
export interface LinkResult {
	linked: true;
	/** Another account was merged into this one. */
	merged: boolean;
}

/** POST /me/accounts/link, 409: that sign-in already has its own account. */
export type AccountConflict = ApiErrorBody & { account: MergeCandidate };

/** GET /groups/:id/next */
export interface NextSuggestion {
	/** The date the rhythm points at (YYYY-MM-DD). */
	target: string;
	/** The free time nearest it (within a week or so either side), or null if none. */
	slot: Slot | null;
}

/** GET /invites/:token: what the invite page shows before joining. */
export interface InvitePreview {
	groupName: string;
	inviterName: string;
	memberCount: number;
	expiresAt: string;
	/** Who an emailed invite is for (null for a link anyone can use). */
	email: string | null;
	/** Who it was sent to by username, or null. */
	username: string | null;
	/** Sent to one person's Activity, so they can decline it. */
	personal: boolean;
}

/** POST /groups/:id/invites (token, url and expiresAt are the first invite's). */
export interface InvitesCreated {
	token: string;
	url: string;
	expiresAt: string;
	invites: GroupInvite[];
}

/** POST /invites/:token/accept */
export type JoinedGroup = Group & {
	/** False when you were already a member. */
	joined: boolean;
};

/** GET /groups/:id/messages */
export interface MessagePage {
	/** Newest last. */
	messages: Message[];
	hasMore: boolean;
}

/** GET /notifications */
export interface NotificationList {
	notifications: AppNotification[];
	unread: number;
}

/** POST /requirements/preview: slots the timing produces before anyone's busy time. */
export interface RequirementPreview {
	count: number;
	from: string;
	to: string;
	summary: string;
}

/** POST /groups/:id/planner/draft */
export interface ProposalDraft {
	title: string;
	note: string;
}

/** GET /regions/detect (best-effort, from request headers). */
export interface DetectedRegion {
	country: string | null;
	subdivision: string | null;
	timezone: string | null;
}

/** A country or subdivision (GET /regions/countries, /regions/subdivisions). */
export interface Region {
	code: string;
	name: string;
}

/** GET /regions/school-authorities */
export interface SchoolAuthority {
	id: string;
	name: string;
}

/** GET /holidays */
export interface Holiday {
	/** YYYY-MM-DD */
	date: string;
	name: string;
}
