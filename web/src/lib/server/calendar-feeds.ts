/**
 * Subscribable calendars (webcal): each user has one secret link listing their groups' plans
 * (optionally one group's), which calendar apps poll, so edits and cancellations reach them —
 * unlike a one-off .ics. The link can be reset if it leaks.
 */
import { createHash } from 'node:crypto';
import { and, eq, gt, inArray } from 'drizzle-orm';
import { db } from './db';
import { calendarFeeds, events, groupMembers, groups, proposalResponses } from './db/schema';
import { decryptJson, encryptJson } from './crypto';
import { env } from './env';
import { notFound } from './http';
import { newToken } from './ids';
import { buildFeed, type FeedItem } from './ics';
import { toGroupEvent } from './events';

/** Plans that ended longer ago than this drop out of the feed. */
const PAST_DAYS = 60;

const hash = (token: string) => createHash('sha256').update(token).digest('hex');

export interface CalendarFeed {
	/** https link (for "copy link" and Google Calendar's "From URL"). */
	url: string;
	/** The same as webcal://, which Apple Calendar and Outlook subscribe to when opened. */
	webcalUrl: string;
}

function toFeed(token: string): CalendarFeed {
	const url = `${env.baseUrl}/calendar/${token}.ics`;
	return { url, webcalUrl: url.replace(/^https?:/, 'webcal:') };
}

/** Your feed's link, made the first time it's asked for. */
export async function getCalendarFeed(userId: string): Promise<CalendarFeed> {
	const row = await db.query.calendarFeeds.findFirst({ where: eq(calendarFeeds.userId, userId) });
	if (row) return toFeed(decryptJson<string>(row.tokenSealed, `calendar-feed:${userId}`));
	return resetCalendarFeed(userId);
}

/** A new link; the old one stops working. */
export async function resetCalendarFeed(userId: string): Promise<CalendarFeed> {
	const token = newToken();
	const values = { userId, tokenHash: hash(token), tokenSealed: encryptJson(token, `calendar-feed:${userId}`) };
	await db.insert(calendarFeeds).values(values).onDuplicateKeyUpdate({ set: { tokenHash: values.tokenHash, tokenSealed: values.tokenSealed, createdAt: new Date() } });
	return toFeed(token);
}

/** The feed's calendar file, or 404 for an unknown (or reset) link. `groupId` narrows it to one group. */
export async function renderCalendarFeed(token: string, groupId: string | null, host: string, now = Date.now()): Promise<string> {
	const row = token ? await db.query.calendarFeeds.findFirst({ where: eq(calendarFeeds.tokenHash, hash(token)) }) : null;
	if (!row) throw notFound('Not found');
	const userId = row.userId;

	const memberships = await db
		.select({ groupId: groups.id, name: groups.name, requirement: groups.requirement })
		.from(groupMembers)
		.innerJoin(groups, eq(groups.id, groupMembers.groupId))
		.where(eq(groupMembers.userId, userId));
	const mine = groupId ? memberships.filter((m) => m.groupId === groupId) : memberships;
	if (groupId && !mine.length) throw notFound('Not found');
	if (!mine.length) return buildFeed('Availabliss', [], host);
	const byGroup = new Map(mine.map((m) => [m.groupId, m]));

	const rows = await db
		.select()
		.from(events)
		.where(and(inArray(events.groupId, [...byGroup.keys()]), gt(events.end, new Date(now - PAST_DAYS * 86_400_000))));
	// Leave out plans you said you can't make.
	const declined = new Set(
		(
			await db
				.select({ proposalId: proposalResponses.proposalId })
				.from(proposalResponses)
				.where(and(eq(proposalResponses.userId, userId), eq(proposalResponses.response, 'declined')))
		).map((r) => r.proposalId)
	);
	const items: FeedItem[] = rows
		.filter((r) => !(r.proposalId && declined.has(r.proposalId)))
		.map((r) => {
			const group = byGroup.get(r.groupId)!;
			return { event: toGroupEvent(r), groupName: group.name, timeZone: group.requirement.timezone, updatedAt: r.updatedAt };
		});
	const name = groupId ? `${mine[0].name} · Availabliss` : 'Availabliss';
	return buildFeed(name, items, host);
}
