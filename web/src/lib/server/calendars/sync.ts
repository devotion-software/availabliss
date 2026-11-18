/**
 * One sync of one server-side connection: refresh credentials, list calendars,
 * bring busy time up to date reading as little as each calendar's change
 * tracking allows, keep change notifications alive, and record the outcome.
 * Callers go through `syncConnection` (scheduling.ts), which keeps syncs of a
 * connection from overlapping.
 */
import { and, eq, gte } from 'drizzle-orm';
import { db } from '../db';
import { busyIntervals, calendarConnections, calendars, user } from '../db/schema';
import { decryptJson, encryptJson } from '../crypto';
import { publishAvailabilityChanged } from '../realtime';
import { recordFailure } from '../failures';
import { adapters, credentialContext } from './adapters';
import { inCalendars, overlapping, saveCalendars, toRows, writeBusy, writeEvents } from './busy-store';
import { horizonFor, planCoverage, type Coverage } from './coverage';
import { maintainPush } from './push';
import { ReauthRequired, type CaldavCredentials, type CalendarAdapter, type OAuthCredentials, type RemoteCalendar } from './types';

/** Change cursors start a little before the sync did, in case our clock and the provider's disagree. */
const CURSOR_SKEW_MS = 5 * 60 * 1000;
/** Stored as the cursor of calendars re-read on a schedule, once their first full read is done. */
const SCHEDULED = 'scheduled';
/** Server-side connections are refreshed in the background this often… */
const STALE_AFTER_MS = 30 * 60 * 1000;
/**
 * …or this often when changes reach us another way (a backstop for missed ones):
 * every busy calendar sends change notifications, or every CalDAV calendar has a
 * change tag that `checkCaldavConnections` reads every few minutes.
 */
const TRACKED_STALE_AFTER_MS = 6 * 60 * 60 * 1000;
/** Failing connections back off: 30 min, 1 h, 2 h … up to a day. */
const MAX_BACKOFF_MS = 24 * 60 * 60 * 1000;
const backoff = (failures: number) => Math.min(STALE_AFTER_MS * 2 ** Math.max(0, failures - 1), MAX_BACKOFF_MS);

export interface SyncOptions {
	/**
	 * Set when change notifications triggered the sync: the changed events' ids where
	 * the provider names them (null = unknown). Calendars without change tracking
	 * re-read their far tier when one of these may lie past the near tier.
	 */
	pushedEvents?: (string | null)[];
}

async function timezoneOf(userId: string): Promise<string> {
	const row = await db.query.user.findFirst({ where: eq(user.id, userId), columns: { timezone: true } });
	return row?.timezone ?? 'UTC';
}

/**
 * Brings one connection's stored busy time up to date for `c`, reading as
 * little as each calendar's change tracking allows. Cursors are saved last, so
 * a sync that fails part-way simply re-reads next time.
 */
async function syncBusy<Creds extends OAuthCredentials | CaldavCredentials>(
	adapter: CalendarAdapter<Creds>,
	creds: Creds,
	userId: string,
	remote: RemoteCalendar[],
	saved: { ids: Map<string, string>; cursors: Map<string, string | null> },
	c: Coverage,
	timeZone: string
): Promise<{ changed: boolean; farSynced: boolean }> {
	let changed = false;
	let farSynced = false;
	const cursorAt = new Date(c.now.getTime() - CURSOR_SKEW_MS).toISOString();
	const newCursors = new Map<string, string>();
	// Ranges to read, each shared by every calendar that needs it so providers can batch.
	const reads = new Map<string, { from: Date; to: Date; calendars: RemoteCalendar[] }>();
	const read = (cal: RemoteCalendar, from: Date, to: Date) => {
		if (to <= from) return;
		const key = `${from.getTime()}|${to.getTime()}`;
		if (!reads.has(key)) reads.set(key, { from, to, calendars: [] });
		reads.get(key)!.calendars.push(cal);
	};

	// Coverage shrank (a far-off group went away): drop what's past the new end.
	const allIds = [...saved.ids.values()];
	if (c.covered && c.until < c.covered && allIds.length) {
		changed = (await writeBusy(userId, and(inCalendars(allIds), gte(busyIntervals.start, c.until)), [])) || changed;
	}

	for (const cal of remote) {
		const id = saved.ids.get(cal.externalId);
		if (!id) continue;
		const cursor = saved.cursors.get(id) ?? null;
		// What's stored for this calendar is complete up to here; past it is read fresh.
		const keptUntil = cursor !== null && c.covered ? new Date(Math.min(c.covered.getTime(), c.until.getTime())) : null;

		if (keptUntil && cal.changes === 'cursor' && adapter.changes && Number.isFinite(Date.parse(cursor!))) {
			const events = await adapter.changes(creds, cal, new Date(cursor!), c.from, keptUntil, timeZone);
			if (events) {
				changed = (await writeEvents(userId, id, events, c.from, keptUntil)) || changed;
				read(cal, keptUntil, c.until);
				newCursors.set(id, cursorAt);
				continue;
			}
		} else if (keptUntil && cal.changes === 'version' && cal.version && cursor === cal.version) {
			read(cal, keptUntil, c.until);
			continue;
		} else if (keptUntil && cal.changes === 'none' && cursor === SCHEDULED) {
			read(cal, c.from, c.nearEnd);
			if (c.farDue) {
				read(cal, c.nearEnd, c.until);
				farSynced = true;
			}
			continue;
		}

		// First sync of this calendar, a changed one, or a cursor the provider no longer accepts: read it all.
		read(cal, c.from, c.until);
		newCursors.set(id, cal.changes === 'cursor' ? cursorAt : cal.changes === 'version' && cal.version ? cal.version : SCHEDULED);
		if (cal.changes === 'none') farSynced = true;
	}

	for (const r of reads.values()) {
		const busy = await adapter.busy(creds, r.calendars, r.from, r.to, timeZone);
		const ids = r.calendars.map((cal) => saved.ids.get(cal.externalId)!);
		changed = (await writeBusy(userId, and(inCalendars(ids), overlapping(r.from, r.to)), toRows(saved.ids, busy, r.from, r.to))) || changed;
	}
	for (const [id, cursor] of newCursors) await db.update(calendars).set({ syncCursor: cursor }).where(eq(calendars.id, id));
	return { changed, farSynced };
}

export async function syncConnectionOnce(connectionId: string, options: SyncOptions): Promise<void> {
	const row = await db.query.calendarConnections.findFirst({ where: eq(calendarConnections.id, connectionId) });
	if (!row || row.provider === 'device' || !row.credentials) return;
	const adapter = adapters[row.provider];
	const context = credentialContext(row.id);
	let changed = false;

	try {
		let creds = decryptJson<OAuthCredentials | CaldavCredentials>(row.credentials, context);
		const refreshed = await adapter.refresh?.(creds);
		if (refreshed) {
			creds = refreshed;
			await db
				.update(calendarConnections)
				.set({ credentials: encryptJson(creds, context) })
				.where(eq(calendarConnections.id, row.id));
		}

		const coverage = planCoverage(row, await horizonFor(row.userId));
		if (options.pushedEvents?.length && adapter.changeIsFar && !coverage.farDue) {
			coverage.farDue = await farChange(adapter, creds, options.pushedEvents, coverage.nearEnd);
		}
		const remote = await adapter.listCalendars(creds);
		const saved = await saveCalendars(row.id, remote);
		const result = await syncBusy(adapter, creds, row.userId, remote, saved, coverage, await timezoneOf(row.userId));
		changed = result.changed || saved.changed;
		const tracked =
			row.provider === 'caldav'
				? remote.length > 0 && remote.every((c) => c.changes === 'version')
				: await maintainPush(adapter, creds, row.provider, row.id, remote);
		await db
			.update(calendarConnections)
			.set({
				status: 'ok',
				lastError: null,
				lastSyncedAt: new Date(),
				failures: 0,
				nextSyncAt: new Date(Date.now() + (tracked ? TRACKED_STALE_AFTER_MS : STALE_AFTER_MS)),
				coveredUntil: coverage.until,
				...(result.farSynced && { farSyncedAt: coverage.now })
			})
			.where(eq(calendarConnections.id, row.id));
	} catch (err) {
		const status = err instanceof ReauthRequired ? 'reauth_required' : 'error';
		await recordFailure('calendars', `calendar-sync:${row.provider}`, 'sync failed', { provider: row.provider, connectionId: row.id, err });
		await db
			.update(calendarConnections)
			.set({
				status,
				lastError: String(err instanceof Error ? err.message : err).slice(0, 255),
				// Stamped on failure too, so a broken connection waits its turn instead of
				// coming back first on every run.
				failures: row.failures + 1,
				nextSyncAt: new Date(Date.now() + backoff(row.failures + 1))
			})
			.where(eq(calendarConnections.id, row.id));
	}
	// Most syncs find nothing new: only make every group refresh when busy time changed.
	if (changed) await publishAvailabilityChanged(row.userId);
}

/** Whether any notified change may lie past the near tier (a failed lookup counts as yes). */
async function farChange<Creds>(adapter: CalendarAdapter<Creds>, creds: Creds, eventIds: (string | null)[], nearEnd: Date): Promise<boolean> {
	for (const id of new Set(eventIds)) {
		if (await adapter.changeIsFar!(creds, id, nearEnd).catch(() => true)) return true;
	}
	return false;
}
