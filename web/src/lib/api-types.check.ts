/**
 * Compile-time only (`bun run check`): the server functions behind the endpoints return exactly
 * the shapes docs/openapi.json describes for them in api-types.ts. Nothing here runs.
 */
import type * as A from './api-types';
import type { previewInvite } from './server/groups';
import type { myPlans, upcomingFor } from './server/events';
import type { getCalendarFeed } from './server/calendar-feeds';
import type { nextSuggestion } from './server/rhythm';
import type { listMessages, searchMessages } from './server/chat';
import type { listGroupMedia } from './server/attachments';
import type { listNotifications } from './server/notifications';
import type { previewRequirement } from './server/availability/service';
import type { draftProposal } from './server/planner';
import type { countries, detectRegion, schoolAuthorities, subdivisions } from './server/holidays';
import type { LinkResult } from './server/accounts';
import type { dbHealth } from './server/db';
import type { redisHealth } from './server/redis';

type Returns<F extends (...args: never[]) => unknown> = Awaited<ReturnType<F>>;
/** True when A and B are assignable both ways (same shape, ignoring doc comments). */
type Same<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;
type Assert<T extends true> = T;

export type ApiTypeChecks = [
	Assert<Same<Returns<typeof previewInvite>, A.InvitePreview>>,
	Assert<Same<Returns<typeof myPlans>, A.MyPlans>>,
	Assert<Same<Returns<typeof upcomingFor>, A.UpcomingEvent[]>>,
	Assert<Same<Returns<typeof getCalendarFeed>, A.CalendarFeed>>,
	Assert<Same<Returns<typeof nextSuggestion>, A.NextSuggestion | null>>,
	Assert<Same<Returns<typeof listMessages>, A.MessagePage>>,
	Assert<Same<Returns<typeof searchMessages>, A.MessageSearch>>,
	Assert<Same<Returns<typeof listGroupMedia>, A.GroupMediaPage>>,
	Assert<Same<Returns<typeof listNotifications>, A.NotificationList>>,
	// The route adds `summary` to what previewRequirement works out.
	Assert<Same<Returns<typeof previewRequirement> & { summary: string }, A.RequirementPreview>>,
	Assert<Same<Returns<typeof draftProposal>, A.ProposalDraft>>,
	Assert<Same<Returns<typeof countries>, A.Region[]>>,
	Assert<Same<Returns<typeof subdivisions>, A.Region[]>>,
	Assert<Same<Returns<typeof schoolAuthorities>, A.SchoolAuthority[]>>,
	Assert<Same<ReturnType<typeof detectRegion>, A.DetectedRegion>>,
	Assert<Same<Extract<LinkResult, { linked: true }>, A.LinkResult>>,
	Assert<Same<Returns<typeof dbHealth>, A.Health['db']>>,
	Assert<Same<Returns<typeof redisHealth>, A.Health['redis']>>
];
