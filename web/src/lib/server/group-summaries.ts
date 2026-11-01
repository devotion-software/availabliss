/** The groups list: one card per group with its badge (matches / needs reply / confirmed). */
import { and, asc, eq, gt } from 'drizzle-orm';
import type { GroupStatus, GroupSummary, Role } from '#lib/types';
import { db } from './db';
import { events } from './db/schema';
import { groupBase, groupsFor, listMembers, type GroupRow } from './groups';
import { awaitingReply } from './proposals';
import { unreadCount } from './chat';
import { upcomingSlotCount } from './availability/service';
import { slotNoun } from './availability/labels';

const PREVIEW_SIZE = 5;

async function statusFor(group: GroupRow, userId: string): Promise<GroupStatus> {
	const proposalId = await awaitingReply(group.id, userId);
	if (proposalId) return { kind: 'needs_reply', proposalId };

	const next = await db.query.events.findFirst({
		where: and(eq(events.groupId, group.id), gt(events.start, new Date())),
		orderBy: asc(events.start)
	});
	if (next) return { kind: 'confirmed', eventId: next.id, start: next.start.toISOString() };

	const count = await upcomingSlotCount(group);
	return count ? { kind: 'matches', count, noun: slotNoun(group.requirement) } : { kind: 'searching' };
}

async function summarise(row: GroupRow, role: Role, userId: string): Promise<GroupSummary> {
	const [members, status, unread] = await Promise.all([listMembers(row.id), statusFor(row, userId), unreadCount(row.id, userId)]);
	const memberPreview = members.slice(0, PREVIEW_SIZE).map(({ userId, name, image }) => ({ userId, name, image }));
	return { ...groupBase(row, role, members), status, unreadCount: unread, memberPreview };
}

export async function groupSummaries(userId: string): Promise<GroupSummary[]> {
	const rows = await groupsFor(userId);
	return Promise.all(rows.map(({ row, role }) => summarise(row, role, userId)));
}
