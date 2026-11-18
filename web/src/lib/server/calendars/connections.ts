/** A user's calendar connections: reading them, connecting accounts, settings, "Sync now" and removal. */
import { and, asc, eq, inArray, ne } from 'drizzle-orm';
import type { CalendarConnection } from '#lib/types';
import { db, isDuplicateKey } from '../db';
import { calendarConnections, calendars } from '../db/schema';
import { notFound } from '../http';
import { newId } from '../ids';
import { decryptJson, encryptJson } from '../crypto';
import { publishAvailabilityChanged } from '../realtime';
import { kv } from '../redis';
import { log } from '../log';
import { adapters, credentialContext, type ConnectionRow, type ServerProvider } from './adapters';
import { caldav, ICLOUD_CALDAV } from './caldav';
import { horizonFor } from './coverage';
import { googleAccountLabel } from './google';
import { microsoftAccountLabel } from './microsoft';
import { exchangeCode, type OAuthProvider } from './oauth';
import { stopPush } from './push';
import { syncConnection } from './scheduling';
import type { CaldavCredentials, OAuthCredentials } from './types';

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

// ─── Connecting server-side providers ─────────────────────────────────────

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

/** "Sync now" pulls a connection at most this often; taps in between get its current state. */
export const SYNC_NOW_GAP_SECONDS = 30;

/**
 * "Sync now" from calendar settings. Once per connection per 30 s (across instances), so
 * repeated taps can't start syncs that overlap each other. Phone calendars upload from the
 * device, so there's nothing to pull for them here.
 */
export async function syncNow(userId: string, connectionId: string): Promise<CalendarConnection> {
	const row = await ownedRow(userId, connectionId);
	if (row.provider !== 'device' && (await kv.setIfAbsent(`calendar-sync:now:${connectionId}`, '1', SYNC_NOW_GAP_SECONDS))) {
		await syncConnection(connectionId);
	}
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
			log.warn('calendars', 'stopping notifications failed', { connectionId: row.id, err });
		}
	}
	await db.delete(calendarConnections).where(eq(calendarConnections.id, connectionId));
	await publishAvailabilityChanged(userId);
}
