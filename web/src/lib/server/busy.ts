/**
 * Loads busy time for users: calendar intervals (from calendars marked busy)
 * merged with manual blocks. Calendar event titles are stored encrypted and only
 * decrypted for their owner (`myBusy`); everyone else only ever gets times.
 */
import { and, count, eq, gt, inArray, isNotNull, lt, min, ne, or } from 'drizzle-orm';
import { BUSY_BUFFER_MINUTES, type BusyInterval } from '#lib/types';
import { db } from './db';
import { busyIgnores, busyIntervals, calendarConnections, calendars, manualBlocks, user } from './db/schema';
import { expandBlock } from './blocks';
import type { Span } from './availability/intervals';
import { badRequest, notFound } from './http';
import { newId } from './ids';
import { openTitle } from './calendars';
import { toBusyBuffer } from '#lib/busy-buffer';

export interface LabelledBusy extends Span {
	manual: boolean;
	label: string | null;
	calendarId: string | null;
	blockId: string | null;
	eventKey: string;
	/** Marked "not busy" by its owner: only kept when asked for (`includeIgnored`). */
	ignoreId: string | null;
	/** The calendar event's name: only loaded for its owner (`withTitles`). */
	title?: string | null;
}

type Ignore = typeof busyIgnores.$inferSelect;

/** The ignore (if any) covering a calendar interval: its whole series, or that occurrence. */
function ignoreFor(ignores: readonly Ignore[], calendarId: string, eventKey: string, start: number, end: number): Ignore | undefined {
	return ignores.find(
		(i) =>
			i.calendarId === calendarId &&
			i.eventKey === eventKey &&
			(i.start === null ? eventKey !== '' : i.start.getTime() === start && (i.end === null || i.end.getTime() === end))
	);
}

/** Users who have connected a calendar or marked any busy time by hand. */
export async function syncedUserIds(userIds: string[]): Promise<Set<string>> {
	if (!userIds.length) return new Set();
	const [connected, blocked] = await Promise.all([
		db.selectDistinct({ userId: calendarConnections.userId }).from(calendarConnections).where(inArray(calendarConnections.userId, userIds)),
		db.selectDistinct({ userId: manualBlocks.userId }).from(manualBlocks).where(inArray(manualBlocks.userId, userIds))
	]);
	return new Set([...connected, ...blocked].map((r) => r.userId));
}

/**
 * How far ahead each user's calendar busy time is known: the earliest end of
 * their connections' coverage (a phone that reads a few months ahead limits a
 * server calendar that reads years). Users without a synced connection are absent.
 */
export async function calendarCoverage(userIds: string[]): Promise<Map<string, number>> {
	if (!userIds.length) return new Map();
	const rows = await db
		.select({ userId: calendarConnections.userId, until: min(calendarConnections.coveredUntil) })
		.from(calendarConnections)
		.where(and(inArray(calendarConnections.userId, userIds), isNotNull(calendarConnections.coveredUntil)))
		.groupBy(calendarConnections.userId);
	return new Map(rows.flatMap((r) => (r.until ? [[r.userId, new Date(r.until).getTime()] as const] : [])));
}

/** The longest buffer a user can put around their busy time (see `User.busyBufferMinutes`). */
const MAX_BUFFER_MS = Math.max(...BUSY_BUFFER_MINUTES) * 60_000;

/**
 * Everyone's busy time in [from, to): calendar intervals (minus events they've marked not busy)
 * and manual blocks. `includeIgnored` keeps the not-busy events too, flagged with `ignoreId`.
 * `buffered` widens each interval by its owner's buffer before and after (for finding free time,
 * never for showing someone their own busy time), so a change to the setting applies at once.
 */
export async function loadBusy(
	userIds: string[],
	from: number,
	to: number,
	includeIgnored = false,
	withTitles = false,
	buffered = false
): Promise<Map<string, LabelledBusy[]>> {
	const result = new Map<string, LabelledBusy[]>(userIds.map((id) => [id, []]));
	if (!userIds.length) return result;
	// A buffer can reach into [from, to) from busy time just outside it.
	if (buffered) {
		from -= MAX_BUFFER_MS;
		to += MAX_BUFFER_MS;
	}
	const range = { from: new Date(from), to: new Date(to) };

	const [calendarRows, blockRows, zones, ignoreRows] = await Promise.all([
		db
			.select({
				userId: busyIntervals.userId,
				start: busyIntervals.start,
				end: busyIntervals.end,
				eventKey: busyIntervals.eventKey,
				calendarId: calendars.id,
				calendarName: calendars.name,
				...(withTitles && { title: busyIntervals.title })
			})
			.from(busyIntervals)
			.innerJoin(calendars, eq(calendars.id, busyIntervals.calendarId))
			.where(
				and(inArray(busyIntervals.userId, userIds), eq(calendars.busy, true), lt(busyIntervals.start, range.to), gt(busyIntervals.end, range.from))
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
		db.select({ id: user.id, timezone: user.timezone, buffer: user.busyBufferMinutes }).from(user).where(inArray(user.id, userIds)),
		db.select().from(busyIgnores).where(inArray(busyIgnores.userId, userIds))
	]);

	for (const r of calendarRows) {
		const start = r.start.getTime(),
			end = r.end.getTime();
		const ignore = ignoreFor(ignoreRows, r.calendarId, r.eventKey, start, end);
		if (ignore && !includeIgnored) continue;
		result.get(r.userId)!.push({
			start,
			end,
			manual: false,
			label: r.calendarName,
			calendarId: r.calendarId,
			blockId: null,
			eventKey: r.eventKey,
			ignoreId: ignore?.id ?? null,
			...(withTitles && { title: openTitle(r.userId, (r as { title?: string | null }).title ?? null) })
		});
	}

	const zoneOf = new Map(zones.map((z) => [z.id, z.timezone]));
	for (const b of blockRows) {
		for (const span of expandBlock(b, from, to, zoneOf.get(b.userId) ?? 'UTC')) {
			result.get(b.userId)!.push({ ...span, manual: true, label: b.note, calendarId: null, blockId: b.id, eventKey: '', ignoreId: null });
		}
	}

	if (buffered) {
		for (const z of zones) {
			const pad = toBusyBuffer(z.buffer) * 60_000;
			if (!pad) continue;
			result.set(
				z.id,
				result.get(z.id)!.map((b) => ({ ...b, start: b.start - pad, end: b.end + pad }))
			);
		}
	}
	for (const list of result.values()) list.sort((a, b) => a.start - b.start);
	return result;
}

/**
 * The signed-in user's own busy time, with labels. Events they've marked not busy are included
 * (with `ignoreId`) so they can be shown faded and marked busy again.
 */
export async function myBusy(userId: string, from: number, to: number): Promise<BusyInterval[]> {
	const list = (await loadBusy([userId], from, to, true, true)).get(userId) ?? [];
	// Which events repeat: more than one stored occurrence of the same key in that calendar.
	const keys = [...new Set(list.filter((b) => b.calendarId && b.eventKey).map((b) => b.eventKey))];
	const repeats = keys.length
		? await db
				.select({ calendarId: busyIntervals.calendarId, eventKey: busyIntervals.eventKey, n: count() })
				.from(busyIntervals)
				.where(and(eq(busyIntervals.userId, userId), inArray(busyIntervals.eventKey, keys)))
				.groupBy(busyIntervals.calendarId, busyIntervals.eventKey)
		: [];
	const recurring = new Set(repeats.filter((r) => r.n > 1).map((r) => `${r.calendarId}|${r.eventKey}`));
	return list.map((b) => ({
		start: new Date(b.start).toISOString(),
		end: new Date(b.end).toISOString(),
		source: b.manual ? 'manual' : 'calendar',
		label: b.label,
		calendarId: b.calendarId,
		blockId: b.blockId,
		...(!b.manual && {
			title: b.title ?? null,
			eventKey: b.eventKey,
			recurring: recurring.has(`${b.calendarId}|${b.eventKey}`),
			ignoreId: b.ignoreId
		})
	}));
}

/** Marks a calendar event as not busy: that occurrence, or (`series`) every occurrence. */
export async function ignoreBusy(
	userId: string,
	input: { calendarId: string; eventKey: string; start: string; end: string; series: boolean }
): Promise<{ id: string }> {
	const owned = await db
		.select({ id: calendars.id })
		.from(calendars)
		.innerJoin(calendarConnections, eq(calendarConnections.id, calendars.connectionId))
		.where(and(eq(calendars.id, input.calendarId), eq(calendarConnections.userId, userId)));
	if (!owned.length) throw notFound('Calendar not found');
	if (input.series && !input.eventKey) throw badRequest("This calendar doesn't say which events repeat");
	const id = newId();
	await db.insert(busyIgnores).values({
		id,
		userId,
		calendarId: input.calendarId,
		eventKey: input.eventKey,
		start: input.series ? null : new Date(input.start),
		end: input.series ? null : new Date(input.end)
	});
	return { id };
}

/** Undoes a "not busy": the event counts as busy again. */
export async function unignoreBusy(userId: string, id: string): Promise<void> {
	const [res] = await db.delete(busyIgnores).where(and(eq(busyIgnores.id, id), eq(busyIgnores.userId, userId)));
	if (res.affectedRows === 0) throw notFound('Not found');
}

/** Which of `userIds` have any busy time overlapping [start, end). Only ids, never labels. */
export async function busyDuring(userIds: string[], start: number, end: number): Promise<string[]> {
	const busy = await loadBusy(userIds, start, end, false, false, true);
	return userIds.filter((id) => busy.get(id)?.some((b) => b.start < end && b.end > start));
}
