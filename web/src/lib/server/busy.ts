/**
 * Loads busy time for users: calendar intervals (from calendars marked busy)
 * merged with manual blocks. Calendar event titles are never stored, so the only
 * label available is the calendar's name, and that is only shown to its owner.
 */
import { and, eq, gt, inArray, lt, ne, or } from 'drizzle-orm';
import type { BusyInterval } from '#lib/types';
import { db } from './db';
import { busyIntervals, calendarConnections, calendars, manualBlocks, user } from './db/schema';
import { expandBlock } from './blocks';
import type { Span } from './availability/intervals';

export interface LabelledBusy extends Span {
	manual: boolean;
	label: string | null;
	calendarId: string | null;
	blockId: string | null;
}

/** Users who have connected a calendar or marked any busy time by hand. */
export async function syncedUserIds(userIds: string[]): Promise<Set<string>> {
	if (!userIds.length) return new Set();
	const [connected, blocked] = await Promise.all([
		db
			.selectDistinct({ userId: calendarConnections.userId })
			.from(calendarConnections)
			.where(inArray(calendarConnections.userId, userIds)),
		db.selectDistinct({ userId: manualBlocks.userId }).from(manualBlocks).where(inArray(manualBlocks.userId, userIds))
	]);
	return new Set([...connected, ...blocked].map((r) => r.userId));
}

export async function loadBusy(userIds: string[], from: number, to: number): Promise<Map<string, LabelledBusy[]>> {
	const result = new Map<string, LabelledBusy[]>(userIds.map((id) => [id, []]));
	if (!userIds.length) return result;
	const range = { from: new Date(from), to: new Date(to) };

	const [calendarRows, blockRows, zones] = await Promise.all([
		db
			.select({
				userId: busyIntervals.userId,
				start: busyIntervals.start,
				end: busyIntervals.end,
				calendarId: calendars.id,
				calendarName: calendars.name
			})
			.from(busyIntervals)
			.innerJoin(calendars, eq(calendars.id, busyIntervals.calendarId))
			.where(
				and(
					inArray(busyIntervals.userId, userIds),
					eq(calendars.busy, true),
					lt(busyIntervals.start, range.to),
					gt(busyIntervals.end, range.from)
				)
			),
		db
			.select()
			.from(manualBlocks)
			.where(
				and(
					inArray(manualBlocks.userId, userIds),
					lt(manualBlocks.start, range.to),
					or(ne(manualBlocks.repeat, 'none'), gt(manualBlocks.end, range.from))
				)
			),
		db.select({ id: user.id, timezone: user.timezone }).from(user).where(inArray(user.id, userIds))
	]);

	for (const r of calendarRows) {
		result.get(r.userId)!.push({
			start: r.start.getTime(),
			end: r.end.getTime(),
			manual: false,
			label: r.calendarName,
			calendarId: r.calendarId,
			blockId: null
		});
	}

	const zoneOf = new Map(zones.map((z) => [z.id, z.timezone]));
	for (const b of blockRows) {
		for (const span of expandBlock(b, from, to, zoneOf.get(b.userId) ?? 'UTC')) {
			result.get(b.userId)!.push({ ...span, manual: true, label: b.note, calendarId: null, blockId: b.id });
		}
	}

	for (const list of result.values()) list.sort((a, b) => a.start - b.start);
	return result;
}

/** The signed-in user's own busy time, with labels. */
export async function myBusy(userId: string, from: number, to: number): Promise<BusyInterval[]> {
	const list = (await loadBusy([userId], from, to)).get(userId) ?? [];
	return list.map((b) => ({
		start: new Date(b.start).toISOString(),
		end: new Date(b.end).toISOString(),
		source: b.manual ? 'manual' : 'calendar',
		label: b.label,
		calendarId: b.calendarId,
		blockId: b.blockId
	}));
}

/** Which of `userIds` have any busy time overlapping [start, end). Only ids, never labels. */
export async function busyDuring(userIds: string[], start: number, end: number): Promise<string[]> {
	const busy = await loadBusy(userIds, start, end);
	return userIds.filter((id) => busy.get(id)?.some((b) => b.start < end && b.end > start));
}
