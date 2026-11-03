/**
 * The groups list: one card per group with its badge (matches / needs reply /
 * confirmed). Everything per group is loaded in a handful of grouped queries
 * across all the user's groups; only the slot count is per group, and cached.
 */
import { and, asc, count, eq, gt, gte, inArray, isNull, ne, or, sql } from 'drizzle-orm';
import type { GroupStatus, GroupSummary, Member } from '#lib/types';
import { db } from './db';
import { events, groupMembers, messageReceipts, messages, proposalResponses, proposals, user } from './db/schema';
import { groupBase, groupsFor, type GroupRow } from './groups';
import { syncedUserIds } from './busy';
import { cachedSlotCount } from './availability/slot-count-cache';
import { slotNoun } from './availability/labels';

const PREVIEW_SIZE = 5;

/** Members of every group, in join order. */
async function membersByGroup(groupIds: string[]): Promise<Map<string, Member[]>> {
	const rows = await db
		.select({
			groupId: groupMembers.groupId,
			userId: groupMembers.userId,
			role: groupMembers.role,
			joinedAt: groupMembers.joinedAt,
			name: user.name,
			image: user.image
		})
		.from(groupMembers)
		.innerJoin(user, eq(user.id, groupMembers.userId))
		.where(inArray(groupMembers.groupId, groupIds))
		.orderBy(asc(groupMembers.joinedAt));
	const synced = await syncedUserIds([...new Set(rows.map((r) => r.userId))]);
	const result = new Map<string, Member[]>(groupIds.map((id) => [id, []]));
	for (const { groupId, ...m } of rows) {
		result.get(groupId)!.push({ ...m, joinedAt: m.joinedAt.toISOString(), synced: synced.has(m.userId) });
	}
	return result;
}

/** An open proposal still waiting on this user, per group. */
async function awaitingByGroup(groupIds: string[], userId: string): Promise<Map<string, string>> {
	const rows = await db
		.select({ groupId: proposals.groupId, id: proposals.id })
		.from(proposals)
		.innerJoin(proposalResponses, eq(proposalResponses.proposalId, proposals.id))
		.where(
			and(
				inArray(proposals.groupId, groupIds),
				eq(proposals.status, 'open'),
				eq(proposalResponses.userId, userId),
				eq(proposalResponses.response, 'pending')
			)
		);
	const result = new Map<string, string>();
	for (const r of rows) if (!result.has(r.groupId)) result.set(r.groupId, r.id);
	return result;
}

/** Each group's next upcoming event. */
async function nextEventByGroup(groupIds: string[]): Promise<Map<string, { id: string; start: Date }>> {
	const now = new Date();
	const firsts = await db
		.select({ groupId: events.groupId, start: sql<Date>`min(${events.start})`.mapWith(events.start) })
		.from(events)
		.where(and(inArray(events.groupId, groupIds), gt(events.start, now)))
		.groupBy(events.groupId);
	if (!firsts.length) return new Map();
	const rows = await db
		.select({ groupId: events.groupId, id: events.id, start: events.start })
		.from(events)
		.where(or(...firsts.map((f) => and(eq(events.groupId, f.groupId), eq(events.start, f.start)))))
		.orderBy(asc(events.id));
	const result = new Map<string, { id: string; start: Date }>();
	for (const r of rows) if (!result.has(r.groupId)) result.set(r.groupId, { id: r.id, start: r.start });
	return result;
}

/**
 * Messages from others this user hasn't seen, per group. History from before
 * they joined is readable but never counts as unread.
 */
async function unreadByGroup(groupIds: string[], userId: string): Promise<Map<string, number>> {
	const rows = await db
		.select({ groupId: messages.groupId, count: count() })
		.from(messages)
		.innerJoin(groupMembers, and(eq(groupMembers.groupId, messages.groupId), eq(groupMembers.userId, userId)))
		.leftJoin(messageReceipts, and(eq(messageReceipts.messageId, messages.id), eq(messageReceipts.userId, userId)))
		.where(
			and(
				inArray(messages.groupId, groupIds),
				gte(messages.createdAt, groupMembers.joinedAt),
				or(isNull(messages.authorId), ne(messages.authorId, userId)),
				isNull(messageReceipts.seenAt)
			)
		)
		.groupBy(messages.groupId);
	return new Map(rows.map((r) => [r.groupId, Number(r.count)]));
}

async function statusFor(
	group: GroupRow,
	awaiting: string | undefined,
	next: { id: string; start: Date } | undefined
): Promise<GroupStatus> {
	if (awaiting) return { kind: 'needs_reply', proposalId: awaiting };
	if (next) return { kind: 'confirmed', eventId: next.id, start: next.start.toISOString() };
	const slots = await cachedSlotCount(group);
	return slots ? { kind: 'matches', count: slots, noun: slotNoun(group.requirement) } : { kind: 'searching' };
}

export async function groupSummaries(userId: string): Promise<GroupSummary[]> {
	const rows = await groupsFor(userId);
	if (!rows.length) return [];
	const ids = rows.map((r) => r.row.id);
	const [members, awaiting, next, unread] = await Promise.all([
		membersByGroup(ids),
		awaitingByGroup(ids, userId),
		nextEventByGroup(ids),
		unreadByGroup(ids, userId)
	]);
	return Promise.all(
		rows.map(async ({ row, role }) => {
			const list = members.get(row.id) ?? [];
			return {
				...groupBase(row, role, list),
				status: await statusFor(row, awaiting.get(row.id), next.get(row.id)),
				unreadCount: unread.get(row.id) ?? 0,
				memberPreview: list.slice(0, PREVIEW_SIZE).map(({ userId, name, image }) => ({ userId, name, image }))
			};
		})
	);
}
