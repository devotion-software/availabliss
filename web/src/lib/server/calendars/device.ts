/** Phone calendars: the apps read them on the device and upload busy time here. */
import { and, eq } from 'drizzle-orm';
import type { CalendarConnection } from '#lib/types';
import { db, isDuplicateKey } from '../db';
import { calendarConnections } from '../db/schema';
import { newId } from '../ids';
import { publishAvailabilityChanged } from '../realtime';
import { kv } from '../redis';
import { inCalendars, overlapping, saveCalendars, toRows, writeBusy } from './busy-store';
import { getConnection } from './connections';

export interface DeviceSyncInput {
	deviceId: string;
	deviceName: string;
	rangeStart: string;
	rangeEnd: string;
	/** End of the whole sync when it's uploaded in several ranges (defaults to `rangeEnd`). */
	coveredUntil?: string;
	/** More ranges of this sync follow: hold the group refresh until the last one. */
	more?: boolean;
	calendars: { externalId: string; name: string; color: string | null; busy?: boolean }[];
	busy: { calendarExternalId: string; start: string; end: string; eventKey?: string; title?: string | null }[];
}

const deviceRefreshKey = (userId: string, deviceId: string) => `device-sync-changed:${userId}:${deviceId}`;

export async function syncDevice(userId: string, input: DeviceSyncInput): Promise<CalendarConnection> {
	const find = () =>
		db.query.calendarConnections.findFirst({
			where: and(eq(calendarConnections.userId, userId), eq(calendarConnections.deviceId, input.deviceId))
		});
	const from = new Date(input.rangeStart);
	const to = new Date(input.rangeEnd);
	const coveredUntil = new Date(Math.max(to.getTime(), input.coveredUntil ? Date.parse(input.coveredUntil) : 0));
	const values = { label: input.deviceName, status: 'ok' as const, lastError: null, lastSyncedAt: new Date(), coveredUntil };
	let existing = await find();
	let id = existing?.id ?? newId();
	if (!existing) {
		try {
			await db.insert(calendarConnections).values({ id, userId, provider: 'device', deviceId: input.deviceId, ...values });
		} catch (err) {
			// Two uploads from a fresh install raced: (user, device) is unique, so use the winner's row.
			if (!isDuplicateKey(err) || !(existing = await find())) throw err;
			id = existing.id;
		}
	}
	if (existing) await db.update(calendarConnections).set(values).where(eq(calendarConnections.id, existing.id));

	const saved = await saveCalendars(id, input.calendars);
	// The app sends everything in its range each time; only the difference is written.
	const ids = [...saved.ids.values()];
	const busy = input.busy.map((b) => ({
		calendarExternalId: b.calendarExternalId,
		eventKey: b.eventKey,
		start: new Date(b.start),
		end: new Date(b.end),
		title: b.title ?? null
	}));
	const busyChanged = ids.length > 0 && (await writeBusy(userId, and(inCalendars(ids), overlapping(from, to)), toRows(saved.ids, busy, from, to)));
	// The apps upload on every foreground: only make every group refresh when something
	// changed, and once per multi-range sync rather than once per range.
	const changed = busyChanged || saved.changed || !existing;
	const held = deviceRefreshKey(userId, input.deviceId);
	if (input.more) {
		if (changed) await kv.set(held, '1', 10 * 60);
	} else if ((await kv.take(held)) !== null || changed) {
		await publishAvailabilityChanged(userId);
	}
	return getConnection(userId, id);
}
