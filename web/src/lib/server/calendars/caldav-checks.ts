/**
 * iCloud and other CalDAV servers can't notify us of changes, so between full
 * syncs their change tags are checked every few minutes.
 */
import { and, asc, eq } from 'drizzle-orm';
import { db } from '../db';
import { calendarConnections, calendars } from '../db/schema';
import { decryptJson, encryptJson } from '../crypto';
import { log } from '../log';
import { recordFailure } from '../failures';
import { adapters, credentialContext } from './adapters';
import { pool, syncConnection } from './scheduling';
import type { CaldavCredentials } from './types';

/** CalDAV change-tag checks run this many connections at once. */
const CHECK_CONCURRENCY = 10;

/**
 * Each connection's change tags are read (one request for all its calendars)
 * and the full sync runs only when one moved, or a calendar was added or
 * removed. Calendars without a tag are left to the regular schedule. Failures
 * are left to the full sync, which records them and backs off. Returns whether it synced.
 */
export async function checkConnection(connectionId: string): Promise<boolean> {
	const row = await db.query.calendarConnections.findFirst({ where: eq(calendarConnections.id, connectionId) });
	if (!row || row.provider !== 'caldav' || row.status !== 'ok' || !row.credentials) return false;
	const adapter = adapters.caldav;
	const context = credentialContext(row.id);
	let changed = false;
	try {
		let creds = decryptJson<CaldavCredentials>(row.credentials, context);
		const refreshed = await adapter.refresh?.(creds);
		if (refreshed) {
			creds = refreshed as CaldavCredentials;
			await db.update(calendarConnections).set({ credentials: encryptJson(creds, context) }).where(eq(calendarConnections.id, row.id));
		}
		const remote = await adapter.listCalendars(creds);
		const stored = new Map(
			(await db.select({ externalId: calendars.externalId, cursor: calendars.syncCursor }).from(calendars).where(eq(calendars.connectionId, row.id))).map(
				(c) => [c.externalId, c.cursor]
			)
		);
		changed =
			remote.length !== stored.size ||
			remote.some((c) => !stored.has(c.externalId) || (c.changes === 'version' && c.version !== stored.get(c.externalId)));
	} catch (err) {
		await recordFailure('calendars', 'caldav-check', 'change check failed', { connectionId: row.id, err });
	}
	await db.update(calendarConnections).set({ checkedAt: new Date() }).where(eq(calendarConnections.id, row.id));
	if (changed) await syncConnection(row.id);
	return changed;
}

/** Checks every working CalDAV connection, longest-unchecked first, until `deadline`. Returns how many synced. */
export async function checkCaldavConnections(deadline = Infinity): Promise<number> {
	const startedAt = Date.now();
	const rows = await db
		.select({ id: calendarConnections.id, checkedAt: calendarConnections.checkedAt })
		.from(calendarConnections)
		.where(and(eq(calendarConnections.provider, 'caldav'), eq(calendarConnections.status, 'ok')))
		// NULL (never checked) sorts first.
		.orderBy(asc(calendarConnections.checkedAt))
		.limit(1000);
	const stats = { started: 0 };
	const synced = await pool(rows.map((r) => r.id), CHECK_CONCURRENCY, deadline, checkConnection, stats);
	// The numbers to decide on when to check less often (see the "iCloud change-check load" task):
	// how long a run takes against its budget, and whether connections start being left unchecked.
	const oldest = rows[0]?.checkedAt;
	log.info('calendars', 'CalDAV change check', {
		connections: rows.length,
		checked: stats.started,
		leftForNextRun: rows.length - stats.started,
		synced,
		durationMs: Date.now() - startedAt,
		budgetMs: Number.isFinite(deadline) ? deadline - startedAt : null,
		oldestCheckAgeSeconds: oldest ? Math.round((startedAt - oldest.getTime()) / 1000) : null
	});
	return synced;
}
