/**
 * Writing what a sync found: a connection's calendars, and busy intervals as
 * diffs against what's stored (only start/end times, plus encrypted titles).
 */
import { and, eq, gt, inArray, lt, notInArray, type SQL } from 'drizzle-orm';
import { db } from '../db';
import { busyIntervals, calendars } from '../db/schema';
import { newId } from '../ids';
import { decryptJson, encryptJson } from '../crypto';
import type { ChangedEvents, RemoteBusy, RemoteCalendar } from './types';

/** Calendars that rarely mean "busy" start switched off. */
const NOT_BUSY_BY_DEFAULT = /birthday|holiday|contacts|week numbers|siri suggestions|found in/i;

type CalendarInfo = Pick<RemoteCalendar, 'externalId' | 'name' | 'color'> & { busy?: boolean };

/**
 * Upserts calendars by external id (keeping the user's busy choice) and removes
 * vanished ones. `changed` is false when nothing was written. `cursors` holds
 * each kept calendar's sync cursor (null for new ones).
 */
export async function saveCalendars(
	connectionId: string,
	remote: CalendarInfo[]
): Promise<{ ids: Map<string, string>; cursors: Map<string, string | null>; changed: boolean }> {
	let changed = false;
	const existing = await db.select().from(calendars).where(eq(calendars.connectionId, connectionId));
	const byExternal = new Map(existing.map((c) => [c.externalId, c]));

	for (const cal of remote) {
		const current = byExternal.get(cal.externalId);
		if (current) {
			// Most syncs change nothing: skip the write.
			const busyChanged = cal.busy !== undefined && cal.busy !== current.busy;
			if (current.name === cal.name && current.color === cal.color && !busyChanged) continue;
			changed = true;
			await db
				.update(calendars)
				.set({ name: cal.name, color: cal.color, ...(cal.busy !== undefined && { busy: cal.busy }) })
				.where(eq(calendars.id, current.id));
		} else {
			changed = true;
			const id = newId();
			const busy = cal.busy ?? !NOT_BUSY_BY_DEFAULT.test(cal.name);
			byExternal.set(cal.externalId, {
				id,
				connectionId,
				externalId: cal.externalId,
				name: cal.name,
				color: cal.color,
				busy,
				syncCursor: null,
				pushId: null,
				pushResourceId: null,
				pushExpiresAt: null
			});
			await db.insert(calendars).values({ id, connectionId, externalId: cal.externalId, name: cal.name, color: cal.color, busy });
		}
	}

	const keep = remote.map((c) => c.externalId);
	if (existing.some((c) => !keep.includes(c.externalId))) {
		changed = true;
		await db.delete(calendars).where(and(eq(calendars.connectionId, connectionId), keep.length ? notInArray(calendars.externalId, keep) : undefined));
	}

	const kept = [...byExternal].filter(([ext]) => keep.includes(ext));
	return {
		ids: new Map(kept.map(([ext, c]) => [ext, c.id])),
		cursors: new Map(kept.map(([, c]) => [c.id, c.syncCursor])),
		changed
	};
}

interface BusyRow {
	calendarId: string;
	eventKey: string;
	start: Date;
	end: Date;
	/** Plain text here; encrypted when written. */
	title: string | null;
}

const rowKey = (r: BusyRow) => `${r.calendarId}|${r.eventKey}|${r.start.getTime()}|${r.end.getTime()}|${r.title ?? ''}`;

/** Event titles are encrypted per user, so a row copied to someone else can't be read. */
export const titleContext = (userId: string) => `busy-title:${userId}`;
const MAX_TITLE = 200;
function sealTitle(userId: string, title: string | null): string | null {
	return title ? encryptJson(title.slice(0, MAX_TITLE), titleContext(userId)) : null;
}
export function openTitle(userId: string, stored: string | null): string | null {
	if (!stored) return null;
	try {
		return decryptJson<string>(stored, titleContext(userId));
	} catch {
		return null;
	}
}
export const overlapping = (from: Date, to: Date) => and(lt(busyIntervals.start, to), gt(busyIntervals.end, from));
export const inCalendars = (ids: string[]) => inArray(busyIntervals.calendarId, ids);

/** Busy time from a provider as rows, keeping only what overlaps [from, to) so it stays inside the scope it's written to. */
export function toRows(calendarIds: Map<string, string>, busy: RemoteBusy[], from: Date, to: Date): BusyRow[] {
	return busy.flatMap((b) => {
		const calendarId = calendarIds.get(b.calendarExternalId);
		return calendarId && b.end > b.start && b.start < to && b.end > from
			? [{ calendarId, eventKey: b.eventKey ?? '', start: b.start, end: b.end, title: b.title?.trim() || null }]
			: [];
	});
}

const CHUNK = 1000;

/**
 * Makes the stored rows matching `scope` exactly `rows`, deleting and inserting
 * only the difference. Returns false, writing nothing, when they already match.
 */
export async function writeBusy(userId: string, scope: SQL | undefined, rows: BusyRow[]): Promise<boolean> {
	const wanted = new Map(rows.map((r) => [rowKey(r), r]));
	return db.transaction(async (tx) => {
		const stored = await tx
			.select({
				id: busyIntervals.id,
				calendarId: busyIntervals.calendarId,
				eventKey: busyIntervals.eventKey,
				start: busyIntervals.start,
				end: busyIntervals.end,
				title: busyIntervals.title
			})
			.from(busyIntervals)
			.where(scope);
		// Whatever is stored and still wanted (renames included) stays; the rest of `stored` goes.
		const stale = stored.filter((r) => !wanted.delete(rowKey({ ...r, title: openTitle(userId, r.title) }))).map((r) => r.id);
		if (!stale.length && !wanted.size) return false;

		for (let i = 0; i < stale.length; i += CHUNK) await tx.delete(busyIntervals).where(inArray(busyIntervals.id, stale.slice(i, i + CHUNK)));
		const fresh = [...wanted.values()].map((r) => ({ id: newId(), userId, ...r, title: sealTitle(userId, r.title) }));
		for (let i = 0; i < fresh.length; i += CHUNK) await tx.insert(busyIntervals).values(fresh.slice(i, i + CHUNK));
		return true;
	});
}

/** Replaces the stored busy time of just these events (by key) within [from, to). */
export async function writeEvents(userId: string, calendarId: string, events: ChangedEvents, from: Date, to: Date): Promise<boolean> {
	if (!events.size) return false;
	const busy = [...events.values()].flat();
	const rows = toRows(new Map(busy.map((b) => [b.calendarExternalId, calendarId])), busy, from, to);
	const scope = and(eq(busyIntervals.calendarId, calendarId), inArray(busyIntervals.eventKey, [...events.keys()]), overlapping(from, to));
	return writeBusy(userId, scope, rows);
}
