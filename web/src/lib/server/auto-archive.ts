/**
 * Groups that archive themselves: once a group's latest event has passed and nothing else is
 * planned (no event still to come, no open proposal for a time still ahead), it moves to everyone's
 * Archived section. That happens once per event: unarchived, it stays out until a newer event has
 * passed. A new proposal or event brings an archived group that archives itself back.
 */
import { and, eq, gt, isNull } from 'drizzle-orm';
import { db } from './db';
import { events, groupMembers, groups, proposals } from './db/schema';
import { getGroup, getGroupRow, latestEventEnd } from './groups';
import { publishGroupUpdated } from './realtime';

/** Tells the group's members it changed (each with their own role). */
async function announceUpdate(groupId: string): Promise<void> {
	const owner = await db.query.groupMembers.findFirst({
		where: and(eq(groupMembers.groupId, groupId), eq(groupMembers.role, 'owner')),
		columns: { userId: true }
	});
	if (owner) await publishGroupUpdated(await getGroup(groupId, owner.userId));
}

/** Whether the group has an event still to come (or under way), or an open proposal for a time still ahead. */
async function hasPlans(groupId: string, now: Date): Promise<boolean> {
	const [event] = await db
		.select({ id: events.id })
		.from(events)
		.where(and(eq(events.groupId, groupId), gt(events.end, now)))
		.limit(1);
	if (event) return true;
	const [proposal] = await db
		.select({ id: proposals.id })
		.from(proposals)
		.where(and(eq(proposals.groupId, groupId), eq(proposals.status, 'open'), gt(proposals.end, now)))
		.limit(1);
	return proposal != null;
}

/** Archives every group that archives itself and whose latest event has now passed. Returns their ids. */
export async function autoArchiveGroups(now = new Date()): Promise<string[]> {
	const candidates = await db
		.select({ id: groups.id, through: groups.autoArchivedThrough })
		.from(groups)
		.where(and(eq(groups.autoArchive, true), isNull(groups.archivedAt)));
	const archived: string[] = [];
	for (const group of candidates) {
		if (await hasPlans(group.id, now)) continue;
		const passed = await latestEventEnd(group.id, now);
		// No event yet, or this one already archived it (or had passed when someone unarchived it).
		if (!passed || (group.through && passed <= group.through)) continue;
		// Only if nobody changed it meanwhile.
		const [res] = await db
			.update(groups)
			.set({ archivedAt: now, autoArchivedThrough: passed })
			.where(and(eq(groups.id, group.id), eq(groups.autoArchive, true), isNull(groups.archivedAt)));
		if (!res.affectedRows) continue;
		archived.push(group.id);
		await announceUpdate(group.id);
	}
	return archived;
}

/** Something new was planned: a group that archives itself comes back out of the Archived section. */
export async function unarchiveOnActivity(groupId: string): Promise<void> {
	const row = await getGroupRow(groupId);
	if (!row.autoArchive || !row.archivedAt) return;
	await db.update(groups).set({ archivedAt: null }).where(eq(groups.id, groupId));
	await announceUpdate(groupId);
}
