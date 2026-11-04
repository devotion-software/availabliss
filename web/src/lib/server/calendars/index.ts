/**
 * Calendar connections: linking providers, choosing which calendars mean busy,
 * and keeping `busy_intervals` up to date. Only start/end times are stored.
 *
 * Busy time is kept from a week back to a horizon at least three years ahead,
 * further when one of the user's groups plans past that (up to ten years).
 * Syncs only move what changed: Google lists changed events, CalDAV calendars
 * whose change tag hasn't moved are skipped, and calendars with no way to see
 * changes (Outlook, some CalDAV servers) re-read the next six months every sync
 * and the rest daily. Writes are diffs against what's stored.
 */
import { and, asc, eq, gt, gte, inArray, isNull, lt, lte, ne, notInArray, or, type SQL } from 'drizzle-orm';
import type { CalendarConnection, CalendarProvider } from '#lib/types';
import { db, isDuplicateKey } from '../db';
import { busyIntervals, calendarConnections, calendars, groupMembers, groups, user } from '../db/schema';
import { notFound } from '../http';
import { newId } from '../ids';
import { decryptJson, encryptJson } from '../crypto';
import { publishAvailabilityChanged } from '../realtime';
import { kv } from '../redis';
import { caldav, ICLOUD_CALDAV } from './caldav';
import { google, googleAccountLabel } from './google';
import { microsoft, microsoftAccountLabel } from './microsoft';
import { exchangeCode, type OAuthProvider } from './oauth';
import { maintainPush, stopPush } from './push';
import {
	ReauthRequired,
	type CaldavCredentials,
	type CalendarAdapter,
	type ChangedEvents,
	type OAuthCredentials,
	type RemoteBusy,
	type RemoteCalendar
} from './types';

const DAY_MS = 24 * 60 * 60 * 1000;
/** Busy time is kept from this far back (for today's view). */
const HISTORY_DAYS = 7;
/** Everyone's calendars are covered at least this far ahead… */
const DEFAULT_HORIZON_DAYS = 3 * 365;
/** …and as far as their groups plan, up to this. */
const MAX_HORIZON_DAYS = 10 * 365;
/** Slots near the end of a group's range still look at busy time a little past it. */
const RANGE_SLACK_DAYS = 15;
/** Coverage grows in steps, so the rolling horizon costs one extra fetch a month, not one a day. */
const EXTEND_STEP_DAYS = 30;
/** Calendars without change tracking: re-read this far ahead on every sync… */
const NEAR_DAYS = 183;
/** …and the rest of their range this often. */
const FAR_EVERY_MS = DAY_MS;
/** Change cursors start a little before the sync did, in case our clock and the provider's disagree. */
const CURSOR_SKEW_MS = 5 * 60 * 1000;
/** Stored as the cursor of calendars re-read on a schedule, once their first full read is done. */
const SCHEDULED = 'scheduled';
/** Server-side connections are refreshed in the background this often… */
const STALE_AFTER_MS = 30 * 60 * 1000;
/** …or this often when every busy calendar sends change notifications (a backstop for missed ones). */
const PUSHED_STALE_AFTER_MS = 6 * 60 * 60 * 1000;
/** Failing connections back off: 30 min, 1 h, 2 h … up to a day. */
const MAX_BACKOFF_MS = 24 * 60 * 60 * 1000;
const backoff = (failures: number) => Math.min(STALE_AFTER_MS * 2 ** Math.max(0, failures - 1), MAX_BACKOFF_MS);
/** Background syncs run this many connections at once. */
const SYNC_CONCURRENCY = 5;
/** Calendars that rarely mean "busy" start switched off. */
const NOT_BUSY_BY_DEFAULT = /birthday|holiday|contacts|week numbers|siri suggestions|found in/i;

type ConnectionRow = typeof calendarConnections.$inferSelect;
type ServerProvider = Exclude<CalendarProvider, 'device'>;

/** Exported so tests can stand in for a provider. */
export const adapters: Record<ServerProvider, CalendarAdapter<OAuthCredentials | CaldavCredentials>> = {
	google: google as CalendarAdapter<OAuthCredentials | CaldavCredentials>,
	microsoft: microsoft as CalendarAdapter<OAuthCredentials | CaldavCredentials>,
	caldav: caldav as CalendarAdapter<OAuthCredentials | CaldavCredentials>
};

// ─── Reads ────────────────────────────────────────────────────────────────

async function toConnections(rows: ConnectionRow[]): Promise<CalendarConnection[]> {
	if (!rows.length) return [];
	// Phones are told how far ahead to read; every row here belongs to one user.
	const deviceHorizon = rows.some((r) => r.provider === 'device') ? (await horizonFor(rows[0].userId)).toISOString() : null;
	const cals = await db
		.select()
		.from(calendars)
		.where(inArray(calendars.connectionId, rows.map((r) => r.id)))
		.orderBy(asc(calendars.name));
	return rows.map((r) => ({
		id: r.id,
		provider: r.provider,
		label: r.label,
		status: r.status,
		lastSyncedAt: r.lastSyncedAt?.toISOString() ?? null,
		calendars: cals
			.filter((c) => c.connectionId === r.id)
			.map((c) => ({ id: c.id, name: c.name, color: c.color, busy: c.busy })),
		syncUntil: r.provider === 'device' ? deviceHorizon : null
	}));
}

export async function listConnections(userId: string): Promise<CalendarConnection[]> {
	const rows = await db
		.select()
		.from(calendarConnections)
		.where(eq(calendarConnections.userId, userId))
		.orderBy(asc(calendarConnections.createdAt));
	return toConnections(rows);
}

async function ownedRow(userId: string, connectionId: string): Promise<ConnectionRow> {
	const row = await db.query.calendarConnections.findFirst({
		where: and(eq(calendarConnections.id, connectionId), eq(calendarConnections.userId, userId))
	});
	if (!row) throw notFound('Calendar connection not found');
	return row;
}

export async function getConnection(userId: string, connectionId: string): Promise<CalendarConnection> {
	return (await toConnections([await ownedRow(userId, connectionId)]))[0];
}

// ─── Horizon ──────────────────────────────────────────────────────────────

/**
 * How far ahead a user's busy time should be known: three years, or the end of
 * the furthest-off range any of their groups is searching, up to ten years.
 */
export async function horizonFor(userId: string, now = Date.now()): Promise<Date> {
	const rows = await db
		.select({ requirement: groups.requirement })
		.from(groupMembers)
		.innerJoin(groups, eq(groups.id, groupMembers.groupId))
		.where(eq(groupMembers.userId, userId));
	const ends = rows.map((r) => Date.parse(`${r.requirement.range.to}T00:00:00Z`) + RANGE_SLACK_DAYS * DAY_MS).filter(Number.isFinite);
	const wanted = Math.max(now + DEFAULT_HORIZON_DAYS * DAY_MS, ...ends);
	return new Date(Math.min(wanted, now + MAX_HORIZON_DAYS * DAY_MS));
}

/**
 * Call when groups may now plan further ahead (a group's range changed, or
 * someone joined one): server connections that don't reach the users' new
 * horizon are made due, so the next background run extends them.
 */
export async function refreshCoverage(userIds: string[]): Promise<void> {
	try {
		for (const userId of new Set(userIds)) {
			const horizon = await horizonFor(userId);
			await db
				.update(calendarConnections)
				.set({ nextSyncAt: null })
				.where(
					and(
						eq(calendarConnections.userId, userId),
						ne(calendarConnections.provider, 'device'),
						eq(calendarConnections.status, 'ok'),
						or(isNull(calendarConnections.coveredUntil), lt(calendarConnections.coveredUntil, horizon))
					)
				);
		}
	} catch (err) {
		// Only hurries a sync along: the regular one still extends coverage.
		console.warn('[calendars] refreshCoverage failed', err);
	}
}

/** What one sync of a connection reads, given what's stored and the user's horizon. */
export interface Coverage {
	now: Date;
	/** Start of the stored range. */
	from: Date;
	/** How far ahead busy time is stored now (null = never synced). */
	covered: Date | null;
	/** How far ahead it will be stored after this sync. */
	until: Date;
	/** End of the near tier for calendars without change tracking. */
	nearEnd: Date;
	/** Whether those calendars' far tier is re-read this sync. */
	farDue: boolean;
}

export function planCoverage(
	row: { coveredUntil: Date | null; farSyncedAt: Date | null },
	horizon: Date,
	now = Date.now()
): Coverage {
	const covered = row.coveredUntil;
	const step = EXTEND_STEP_DAYS * DAY_MS;
	const stepped = new Date(horizon.getTime() + step);
	// Grow past the horizon by a step; shrink back once a far-off group has gone.
	const resize = !covered || covered < horizon || covered.getTime() > stepped.getTime() + step;
	const until = resize ? stepped : covered;
	return {
		now: new Date(now),
		from: new Date(now - HISTORY_DAYS * DAY_MS),
		covered,
		until,
		nearEnd: new Date(Math.min(now + NEAR_DAYS * DAY_MS, until.getTime())),
		farDue: resize || !row.farSyncedAt || now - row.farSyncedAt.getTime() >= FAR_EVERY_MS
	};
}

// ─── Shared persistence ───────────────────────────────────────────────────

type CalendarInfo = Pick<RemoteCalendar, 'externalId' | 'name' | 'color'> & { busy?: boolean };

/**
 * Upserts calendars by external id (keeping the user's busy choice) and removes
 * vanished ones. `changed` is false when nothing was written. `cursors` holds
 * each kept calendar's sync cursor (null for new ones).
 */
async function saveCalendars(
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
			byExternal.set(cal.externalId, { id, connectionId, externalId: cal.externalId, name: cal.name, color: cal.color, busy, syncCursor: null, pushId: null, pushResourceId: null, pushExpiresAt: null });
			await db.insert(calendars).values({ id, connectionId, externalId: cal.externalId, name: cal.name, color: cal.color, busy });
		}
	}

	const keep = remote.map((c) => c.externalId);
	if (existing.some((c) => !keep.includes(c.externalId))) {
		changed = true;
		await db
			.delete(calendars)
			.where(and(eq(calendars.connectionId, connectionId), keep.length ? notInArray(calendars.externalId, keep) : undefined));
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
}

const rowKey = (r: BusyRow) => `${r.calendarId}|${r.eventKey}|${r.start.getTime()}|${r.end.getTime()}`;
const overlapping = (from: Date, to: Date) => and(lt(busyIntervals.start, to), gt(busyIntervals.end, from));
const inCalendars = (ids: string[]) => inArray(busyIntervals.calendarId, ids);

/** Busy time from a provider as rows, keeping only what overlaps [from, to) so it stays inside the scope it's written to. */
function toRows(calendarIds: Map<string, string>, busy: RemoteBusy[], from: Date, to: Date): BusyRow[] {
	return busy.flatMap((b) => {
		const calendarId = calendarIds.get(b.calendarExternalId);
		return calendarId && b.end > b.start && b.start < to && b.end > from
			? [{ calendarId, eventKey: b.eventKey ?? '', start: b.start, end: b.end }]
			: [];
	});
}

const CHUNK = 1000;

/**
 * Makes the stored rows matching `scope` exactly `rows`, deleting and inserting
 * only the difference. Returns false, writing nothing, when they already match.
 */
async function writeBusy(userId: string, scope: SQL | undefined, rows: BusyRow[]): Promise<boolean> {
	const wanted = new Map(rows.map((r) => [rowKey(r), r]));
	return db.transaction(async (tx) => {
		const stored = await tx
			.select({ id: busyIntervals.id, calendarId: busyIntervals.calendarId, eventKey: busyIntervals.eventKey, start: busyIntervals.start, end: busyIntervals.end })
			.from(busyIntervals)
			.where(scope);
		// Whatever is stored and still wanted stays; the rest of `stored` goes.
		const stale = stored.filter((r) => !wanted.delete(rowKey(r))).map((r) => r.id);
		if (!stale.length && !wanted.size) return false;

		for (let i = 0; i < stale.length; i += CHUNK) await tx.delete(busyIntervals).where(inArray(busyIntervals.id, stale.slice(i, i + CHUNK)));
		const fresh = [...wanted.values()].map((r) => ({ id: newId(), userId, ...r }));
		for (let i = 0; i < fresh.length; i += CHUNK) await tx.insert(busyIntervals).values(fresh.slice(i, i + CHUNK));
		return true;
	});
}

/** Replaces the stored busy time of just these events (by key) within [from, to). */
async function writeEvents(userId: string, calendarId: string, events: ChangedEvents, from: Date, to: Date): Promise<boolean> {
	if (!events.size) return false;
	const busy = [...events.values()].flat();
	const rows = toRows(new Map(busy.map((b) => [b.calendarExternalId, calendarId])), busy, from, to);
	const scope = and(eq(busyIntervals.calendarId, calendarId), inArray(busyIntervals.eventKey, [...events.keys()]), overlapping(from, to));
	return writeBusy(userId, scope, rows);
}

// ─── Server-side providers ────────────────────────────────────────────────

const credentialContext = (connectionId: string) => `calendar:${connectionId}`;

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

export interface SyncOptions {
	/**
	 * Set when change notifications triggered the sync: the changed events' ids where
	 * the provider names them (null = unknown). Calendars without change tracking
	 * re-read their far tier when one of these may lie past the near tier.
	 */
	pushedEvents?: (string | null)[];
}

/** Pulls calendars and busy time for one server-side connection, and keeps its change notifications alive. */
export async function syncConnection(connectionId: string, options: SyncOptions = {}): Promise<void> {
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
			await db.update(calendarConnections).set({ credentials: encryptJson(creds, context) }).where(eq(calendarConnections.id, row.id));
		}

		const coverage = planCoverage(row, await horizonFor(row.userId));
		if (options.pushedEvents?.length && adapter.changeIsFar && !coverage.farDue) {
			coverage.farDue = await farChange(adapter, creds, options.pushedEvents, coverage.nearEnd);
		}
		const remote = await adapter.listCalendars(creds);
		const saved = await saveCalendars(row.id, remote);
		const result = await syncBusy(adapter, creds, row.userId, remote, saved, coverage, await timezoneOf(row.userId));
		changed = result.changed || saved.changed;
		const watched = await maintainPush(adapter, creds, row.provider, row.id, remote);
		await db
			.update(calendarConnections)
			.set({
				status: 'ok',
				lastError: null,
				lastSyncedAt: new Date(),
				failures: 0,
				nextSyncAt: new Date(Date.now() + (watched ? PUSHED_STALE_AFTER_MS : STALE_AFTER_MS)),
				coveredUntil: coverage.until,
				...(result.farSynced && { farSyncedAt: coverage.now })
			})
			.where(eq(calendarConnections.id, row.id));
	} catch (err) {
		const status = err instanceof ReauthRequired ? 'reauth_required' : 'error';
		console.warn('[calendars] sync failed', row.provider, row.id, err);
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

async function createServerConnection(
	userId: string,
	provider: ServerProvider,
	label: string,
	creds: OAuthCredentials | CaldavCredentials
): Promise<string> {
	// Reconnecting the same account replaces the old connection. (user, provider, label)
	// is unique for server connections, so a racing second connect updates instead.
	const find = () =>
		db.query.calendarConnections.findFirst({
			where: and(eq(calendarConnections.userId, userId), eq(calendarConnections.provider, provider), eq(calendarConnections.label, label))
		});
	// Credentials are encrypted against the row id, so they're encrypted per row.
	const values = (id: string) => ({ credentials: encryptJson(creds, credentialContext(id)), status: 'ok' as const, lastError: null });
	let existing = await find();
	if (!existing) {
		const id = newId();
		try {
			await db.insert(calendarConnections).values({ id, userId, provider, label, ...values(id) });
			await syncConnection(id);
			return id;
		} catch (err) {
			if (!isDuplicateKey(err) || !(existing = await find())) throw err;
		}
	}
	await db.update(calendarConnections).set(values(existing.id)).where(eq(calendarConnections.id, existing.id));
	await syncConnection(existing.id);
	return existing.id;
}

export async function connectOAuth(userId: string, provider: OAuthProvider, code: string): Promise<string> {
	const creds = await exchangeCode(provider, code);
	const label = provider === 'google' ? await googleAccountLabel(creds) : await microsoftAccountLabel(creds);
	return createServerConnection(userId, provider, label, creds);
}

export async function connectCaldav(userId: string, input: { username: string; appPassword: string; serverUrl?: string }) {
	const creds: CaldavCredentials = { serverUrl: input.serverUrl ?? ICLOUD_CALDAV, username: input.username, password: input.appPassword };
	// Fail fast on bad credentials before saving anything.
	await caldav.listCalendars(creds);
	const label = creds.serverUrl === ICLOUD_CALDAV ? `iCloud · ${input.username}` : input.username;
	const id = await createServerConnection(userId, 'caldav', label, creds);
	return getConnection(userId, id);
}

/**
 * Refreshes server-side connections that are due, longest-waiting first, a few
 * at a time. Stops starting new syncs at `deadline` (ms since epoch) so a run
 * ends inside the lock that keeps other instances from overlapping it.
 */
export async function syncStaleConnections(deadline = Infinity): Promise<number> {
	const due = await db
		.select({ id: calendarConnections.id })
		.from(calendarConnections)
		.where(
			and(
				ne(calendarConnections.provider, 'device'),
				ne(calendarConnections.status, 'reauth_required'),
				or(isNull(calendarConnections.nextSyncAt), lte(calendarConnections.nextSyncAt, new Date()))
			)
		)
		// NULL (never synced) sorts first.
		.orderBy(asc(calendarConnections.nextSyncAt))
		.limit(50);

	let next = 0;
	let synced = 0;
	const worker = async () => {
		while (next < due.length && Date.now() < deadline) {
			const { id } = due[next++];
			await syncConnection(id);
			synced++;
		}
	};
	await Promise.all(Array.from({ length: Math.min(SYNC_CONCURRENCY, due.length) }, worker));
	return synced;
}

// ─── Device calendars ─────────────────────────────────────────────────────

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
	busy: { calendarExternalId: string; start: string; end: string }[];
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
	const busy = input.busy.map((b) => ({ calendarExternalId: b.calendarExternalId, start: new Date(b.start), end: new Date(b.end) }));
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

// ─── Settings ─────────────────────────────────────────────────────────────

export async function setCalendarBusy(userId: string, connectionId: string, calendarId: string, busy: boolean) {
	await ownedRow(userId, connectionId);
	const result = await db
		.update(calendars)
		.set({ busy })
		.where(and(eq(calendars.id, calendarId), eq(calendars.connectionId, connectionId)));
	if (!result[0].affectedRows) throw notFound('Calendar not found');
	// Sync soon, so the calendar's change notifications start (or stop) with it.
	await db
		.update(calendarConnections)
		.set({ nextSyncAt: null })
		.where(and(eq(calendarConnections.id, connectionId), ne(calendarConnections.provider, 'device')));
	await publishAvailabilityChanged(userId);
	return getConnection(userId, connectionId);
}

export async function syncNow(userId: string, connectionId: string): Promise<CalendarConnection> {
	await ownedRow(userId, connectionId);
	await syncConnection(connectionId);
	return getConnection(userId, connectionId);
}

export async function removeConnection(userId: string, connectionId: string): Promise<void> {
	const row = await ownedRow(userId, connectionId);
	if (row.provider !== 'device' && row.credentials) {
		// Stop change notifications first; ones we can't stop lapse within days and are ignored.
		try {
			const creds = decryptJson<OAuthCredentials | CaldavCredentials>(row.credentials, credentialContext(row.id));
			const cals = await db.select().from(calendars).where(eq(calendars.connectionId, row.id));
			await stopPush(adapters[row.provider], (await adapters[row.provider].refresh?.(creds)) ?? creds, cals);
		} catch (err) {
			console.warn('[calendars] stopping notifications failed', row.id, err);
		}
	}
	await db.delete(calendarConnections).where(eq(calendarConnections.id, connectionId));
	await publishAvailabilityChanged(userId);
}
