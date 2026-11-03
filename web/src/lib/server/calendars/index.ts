/**
 * Calendar connections: linking providers, choosing which calendars mean busy,
 * and keeping `busy_intervals` up to date. Only start/end times are stored.
 */
import { and, asc, eq, gte, inArray, isNull, lt, lte, ne, notInArray, or } from 'drizzle-orm';
import type { CalendarConnection, CalendarProvider } from '#lib/types';
import { db, isDuplicateKey } from '../db';
import { busyIntervals, calendarConnections, calendars, user } from '../db/schema';
import { notFound } from '../http';
import { newId } from '../ids';
import { decryptJson, encryptJson } from '../crypto';
import { publishAvailabilityChanged } from '../realtime';
import { caldav, ICLOUD_CALDAV } from './caldav';
import { google, googleAccountLabel } from './google';
import { microsoft, microsoftAccountLabel } from './microsoft';
import { exchangeCode, type OAuthProvider } from './oauth';
import {
	ReauthRequired,
	type CaldavCredentials,
	type CalendarAdapter,
	type OAuthCredentials,
	type RemoteBusy,
	type RemoteCalendar
} from './types';

const DAY_MS = 24 * 60 * 60 * 1000;
/** Window kept in sync: a week back (for today's view) to six months ahead. */
const syncWindow = () => ({ from: new Date(Date.now() - 7 * DAY_MS), to: new Date(Date.now() + 183 * DAY_MS) });
/** Server-side connections are refreshed in the background this often. */
const STALE_AFTER_MS = 30 * 60 * 1000;
/** Failing connections back off: 30 min, 1 h, 2 h … up to a day. */
const MAX_BACKOFF_MS = 24 * 60 * 60 * 1000;
const backoff = (failures: number) => Math.min(STALE_AFTER_MS * 2 ** Math.max(0, failures - 1), MAX_BACKOFF_MS);
/** Background syncs run this many connections at once. */
const SYNC_CONCURRENCY = 5;
/** Calendars that rarely mean "busy" start switched off. */
const NOT_BUSY_BY_DEFAULT = /birthday|holiday|contacts|week numbers|siri suggestions|found in/i;

type ConnectionRow = typeof calendarConnections.$inferSelect;
type ServerProvider = Exclude<CalendarProvider, 'device'>;

const ADAPTERS: Record<ServerProvider, CalendarAdapter<OAuthCredentials | CaldavCredentials>> = {
	google: google as CalendarAdapter<OAuthCredentials | CaldavCredentials>,
	microsoft: microsoft as CalendarAdapter<OAuthCredentials | CaldavCredentials>,
	caldav: caldav as CalendarAdapter<OAuthCredentials | CaldavCredentials>
};

// ─── Reads ────────────────────────────────────────────────────────────────

async function toConnections(rows: ConnectionRow[]): Promise<CalendarConnection[]> {
	if (!rows.length) return [];
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
			.map((c) => ({ id: c.id, name: c.name, color: c.color, busy: c.busy }))
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

// ─── Shared persistence ───────────────────────────────────────────────────

/**
 * Upserts calendars by external id (keeping the user's busy choice) and removes
 * vanished ones. `changed` is false when nothing was written.
 */
async function saveCalendars(
	connectionId: string,
	remote: (RemoteCalendar & { busy?: boolean })[]
): Promise<{ ids: Map<string, string>; changed: boolean }> {
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
			byExternal.set(cal.externalId, { id, connectionId, externalId: cal.externalId, name: cal.name, color: cal.color, busy: true });
			await db.insert(calendars).values({
				id,
				connectionId,
				externalId: cal.externalId,
				name: cal.name,
				color: cal.color,
				busy: cal.busy ?? !NOT_BUSY_BY_DEFAULT.test(cal.name)
			});
		}
	}

	const keep = remote.map((c) => c.externalId);
	if (existing.some((c) => !keep.includes(c.externalId))) {
		changed = true;
		await db
			.delete(calendars)
			.where(and(eq(calendars.connectionId, connectionId), keep.length ? notInArray(calendars.externalId, keep) : undefined));
	}

	return { ids: new Map([...byExternal].filter(([ext]) => keep.includes(ext)).map(([ext, c]) => [ext, c.id])), changed };
}

/**
 * Replaces stored busy time for these calendars inside the window. Returns false,
 * writing nothing, when the stored intervals are already exactly these.
 */
async function replaceBusy(userId: string, calendarIds: Map<string, string>, busy: RemoteBusy[], from: Date, to: Date): Promise<boolean> {
	const ids = [...calendarIds.values()];
	if (!ids.length) return false;
	const inWindow = and(inArray(busyIntervals.calendarId, ids), lt(busyIntervals.start, to), gte(busyIntervals.end, from));
	const rows = busy.flatMap((b) => {
		const calendarId = calendarIds.get(b.calendarExternalId);
		return calendarId && b.end > b.start ? [{ id: newId(), userId, calendarId, start: b.start, end: b.end }] : [];
	});
	const key = (r: { calendarId: string; start: Date; end: Date }) => `${r.calendarId}|${r.start.getTime()}|${r.end.getTime()}`;
	return db.transaction(async (tx) => {
		const stored = await tx
			.select({ calendarId: busyIntervals.calendarId, start: busyIntervals.start, end: busyIntervals.end })
			.from(busyIntervals)
			.where(inWindow);
		const before = stored.map(key).sort();
		const after = rows.map(key).sort();
		if (before.length === after.length && before.every((k, i) => k === after[i])) return false;

		await tx.delete(busyIntervals).where(inWindow);
		for (let i = 0; i < rows.length; i += 1000) await tx.insert(busyIntervals).values(rows.slice(i, i + 1000));
		return true;
	});
}

// ─── Server-side providers ────────────────────────────────────────────────

const credentialContext = (connectionId: string) => `calendar:${connectionId}`;

async function timezoneOf(userId: string): Promise<string> {
	const row = await db.query.user.findFirst({ where: eq(user.id, userId), columns: { timezone: true } });
	return row?.timezone ?? 'UTC';
}

/** Pulls calendars and busy time for one server-side connection. */
export async function syncConnection(connectionId: string): Promise<void> {
	const row = await db.query.calendarConnections.findFirst({ where: eq(calendarConnections.id, connectionId) });
	if (!row || row.provider === 'device' || !row.credentials) return;
	const adapter = ADAPTERS[row.provider];
	const context = credentialContext(row.id);
	let changed = false;

	try {
		let creds = decryptJson<OAuthCredentials | CaldavCredentials>(row.credentials, context);
		const refreshed = await adapter.refresh?.(creds);
		if (refreshed) {
			creds = refreshed;
			await db.update(calendarConnections).set({ credentials: encryptJson(creds, context) }).where(eq(calendarConnections.id, row.id));
		}

		const { from, to } = syncWindow();
		const remote = await adapter.listCalendars(creds);
		const saved = await saveCalendars(row.id, remote);
		const busy = await adapter.busy(creds, remote, from, to, await timezoneOf(row.userId));
		changed = (await replaceBusy(row.userId, saved.ids, busy, from, to)) || saved.changed;
		await db
			.update(calendarConnections)
			.set({ status: 'ok', lastError: null, lastSyncedAt: new Date(), failures: 0, nextSyncAt: new Date(Date.now() + STALE_AFTER_MS) })
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
	calendars: { externalId: string; name: string; color: string | null; busy?: boolean }[];
	busy: { calendarExternalId: string; start: string; end: string }[];
}

export async function syncDevice(userId: string, input: DeviceSyncInput): Promise<CalendarConnection> {
	const find = () =>
		db.query.calendarConnections.findFirst({
			where: and(eq(calendarConnections.userId, userId), eq(calendarConnections.deviceId, input.deviceId))
		});
	const values = { label: input.deviceName, status: 'ok' as const, lastError: null, lastSyncedAt: new Date() };
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
	const busyChanged = await replaceBusy(
		userId,
		saved.ids,
		input.busy.map((b) => ({ calendarExternalId: b.calendarExternalId, start: new Date(b.start), end: new Date(b.end) })),
		new Date(input.rangeStart),
		new Date(input.rangeEnd)
	);
	// The apps upload on every foreground: only make every group refresh when something changed.
	if (busyChanged || saved.changed || !existing) await publishAvailabilityChanged(userId);
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
	await publishAvailabilityChanged(userId);
	return getConnection(userId, connectionId);
}

export async function syncNow(userId: string, connectionId: string): Promise<CalendarConnection> {
	await ownedRow(userId, connectionId);
	await syncConnection(connectionId);
	return getConnection(userId, connectionId);
}

export async function removeConnection(userId: string, connectionId: string): Promise<void> {
	await ownedRow(userId, connectionId);
	await db.delete(calendarConnections).where(eq(calendarConnections.id, connectionId));
	await publishAvailabilityChanged(userId);
}
