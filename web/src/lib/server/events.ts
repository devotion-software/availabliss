/** Group events: created directly, or automatically when a proposal is confirmed. */
import { and, asc, eq, gt, gte, inArray, lt } from 'drizzle-orm';
import type { GroupEvent } from '#lib/types';
import { db, type Tx } from './db';
import { events, groupMembers, groups } from './db/schema';
import { badRequest, forbidden, notFound } from './http';
import { newId } from './ids';
import { getGroupRow, memberIds, requireMember } from './groups';
import { publishToGroup } from './realtime';
import { notify } from './notifications';
import { postMessage } from './chat';
import { dayLabel } from './availability/labels';

type EventRow = typeof events.$inferSelect;

export const toGroupEvent = (row: EventRow): GroupEvent => ({
	id: row.id,
	groupId: row.groupId,
	proposalId: row.proposalId,
	title: row.title,
	location: row.location,
	note: row.note,
	start: row.start.toISOString(),
	end: row.end.toISOString(),
	allDay: row.allDay,
	createdBy: row.createdBy,
	createdAt: row.createdAt.toISOString()
});

export interface EventInput {
	title: string;
	start: string;
	end: string;
	allDay: boolean;
	location: string | null;
	note: string | null;
}

async function getEventRow(eventId: string): Promise<EventRow> {
	const row = await db.query.events.findFirst({ where: eq(events.id, eventId) });
	if (!row) throw notFound('Event not found');
	return row;
}

/** Members can read; the creator or an admin can change. */
async function requireEditable(eventId: string, userId: string): Promise<EventRow> {
	const row = await getEventRow(eventId);
	const role = await requireMember(row.groupId, userId);
	if (row.createdBy !== userId && role === 'member') throw forbidden('Only the creator or an admin can change this event');
	return row;
}

export async function getEvent(eventId: string, userId: string): Promise<GroupEvent> {
	const row = await getEventRow(eventId);
	await requireMember(row.groupId, userId);
	return toGroupEvent(row);
}

export async function listEvents(groupId: string, userId: string, from: Date, to: Date): Promise<GroupEvent[]> {
	await requireMember(groupId, userId);
	const rows = await db
		.select()
		.from(events)
		.where(and(eq(events.groupId, groupId), lt(events.start, to), gt(events.end, from)))
		.orderBy(asc(events.start));
	return rows.map(toGroupEvent);
}

/** Inserts the row only; used inside the proposal-confirmation transaction. */
export async function insertEvent(tx: Tx | typeof db, groupId: string, createdBy: string, input: EventInput & { proposalId?: string }) {
	const id = newId();
	await tx.insert(events).values({
		id,
		groupId,
		createdBy,
		proposalId: input.proposalId ?? null,
		title: input.title,
		location: input.location,
		note: input.note,
		start: new Date(input.start),
		end: new Date(input.end),
		allDay: input.allDay
	});
	return id;
}

export async function createEvent(groupId: string, userId: string, input: EventInput): Promise<GroupEvent> {
	await requireMember(groupId, userId);
	const id = await insertEvent(db, groupId, userId, input);
	const event = toGroupEvent(await getEventRow(id));
	const group = await getGroupRow(groupId);

	await postMessage(groupId, userId, { kind: 'event', eventId: id });
	await publishToGroup(groupId, 'event.changed', event);
	await notify(
		(await memberIds(groupId)).filter((m) => m !== userId),
		{
			kind: 'event.created',
			groupId,
			title: group.name,
			body: `${event.title} · ${dayLabel(Date.parse(event.start), group.requirement.timezone)}`,
			data: { eventId: id }
		}
	);
	return event;
}

export async function updateEvent(eventId: string, userId: string, patch: Partial<EventInput>): Promise<GroupEvent> {
	const row = await requireEditable(eventId, userId);
	const { start, end, ...rest } = patch;
	if (new Date(end ?? row.end) <= new Date(start ?? row.start)) throw badRequest('Ends before it starts');
	await db
		.update(events)
		.set({ ...rest, ...(start && { start: new Date(start) }), ...(end && { end: new Date(end) }) })
		.where(eq(events.id, eventId));
	const event = toGroupEvent(await getEventRow(eventId));
	await publishToGroup(row.groupId, 'event.changed', event);
	return event;
}

export async function deleteEvent(eventId: string, userId: string): Promise<void> {
	const row = await requireEditable(eventId, userId);
	await db.delete(events).where(eq(events.id, eventId));
	await publishToGroup(row.groupId, 'event.changed', { id: eventId, deleted: true });
}

/** Upcoming events across every group the user belongs to (next 60 days). */
export async function upcomingFor(userId: string): Promise<(GroupEvent & { groupName: string })[]> {
	const now = new Date();
	const horizon = new Date(now.getTime() + 60 * 24 * 60 * 60 * 1000);
	const groupIds = (
		await db.select({ id: groupMembers.groupId }).from(groupMembers).where(eq(groupMembers.userId, userId))
	).map((r) => r.id);
	if (!groupIds.length) return [];
	const rows = await db
		.select({ event: events, groupName: groups.name })
		.from(events)
		.innerJoin(groups, eq(groups.id, events.groupId))
		.where(and(inArray(events.groupId, groupIds), gte(events.end, now), lt(events.start, horizon)))
		.orderBy(asc(events.start));
	return rows.map((r) => ({ ...toGroupEvent(r.event), groupName: r.groupName }));
}
