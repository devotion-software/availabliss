/**
 * When syncs run: one at a time per connection (across instances), extra rounds
 * for requests that arrive during one, and the background run over due connections.
 */
import { and, asc, isNull, lte, ne, or } from 'drizzle-orm';
import { db } from '../db';
import { calendarConnections } from '../db/schema';
import { kv } from '../redis';
import { syncConnectionOnce, type SyncOptions } from './sync';

/** Background syncs run this many connections at once. */
const SYNC_CONCURRENCY = 5;

const syncLockKey = (connectionId: string) => `calendar-sync:running:${connectionId}`;
const syncAgainKey = (connectionId: string) => `calendar-sync:again:${connectionId}`;
/** Longer than any one sync takes; the lock is released as soon as it finishes. */
const SYNC_LOCK_SECONDS = 5 * 60;
/** At most this many syncs back to back when requests keep arriving during one. */
const MAX_SYNC_ROUNDS = 3;

/**
 * Pulls calendars and busy time for one server-side connection, and keeps its change notifications
 * alive. Only one sync of a connection runs at a time (across instances): two at once would diff
 * against the same stored state and could both insert the same busy block, or write cursors out of
 * order. A request that arrives while one is running isn't dropped: it makes that sync go round
 * again once it's done, with the changed events it named (if any).
 */
export async function syncConnection(connectionId: string, options: SyncOptions = {}): Promise<void> {
	if (!(await kv.setIfAbsent(syncLockKey(connectionId), '1', SYNC_LOCK_SECONDS))) {
		await queueAnotherRound(connectionId, options);
		return;
	}
	try {
		let next: SyncOptions | null = options;
		for (let round = 0; next && round < MAX_SYNC_ROUNDS; round++) {
			await syncConnectionOnce(connectionId, next);
			next = await takeQueuedRound(connectionId);
		}
	} finally {
		await kv.delete(syncLockKey(connectionId));
	}
}

/** Remembers that another round is wanted. Changed event ids accumulate; a plain sync (no ids) wins. */
async function queueAnotherRound(connectionId: string, options: SyncOptions) {
	const before = await kv.get(syncAgainKey(connectionId));
	const known: (string | null)[] | 'full' | null = before ? JSON.parse(before) : null;
	const merged = !options.pushedEvents?.length || known === 'full' ? 'full' : [...(known ?? []), ...options.pushedEvents];
	await kv.set(syncAgainKey(connectionId), JSON.stringify(merged), SYNC_LOCK_SECONDS);
}

async function takeQueuedRound(connectionId: string): Promise<SyncOptions | null> {
	const queued = await kv.take(syncAgainKey(connectionId));
	if (queued === null) return null;
	const parsed = JSON.parse(queued) as (string | null)[] | 'full';
	return parsed === 'full' ? {} : { pushedEvents: parsed };
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

	return pool(
		due.map((r) => r.id),
		SYNC_CONCURRENCY,
		deadline,
		async (id) => {
			await syncConnection(id);
			return true;
		}
	);
}

/** Runs `task` over `ids`, `concurrency` at a time, starting none after `deadline`. Counts the true results. */
export async function pool(
	ids: string[],
	concurrency: number,
	deadline: number,
	task: (id: string) => Promise<boolean>,
	stats?: { started: number }
): Promise<number> {
	let next = 0;
	let count = 0;
	const worker = async () => {
		while (next < ids.length && Date.now() < deadline) {
			if (await task(ids[next++])) count++;
		}
	};
	await Promise.all(Array.from({ length: Math.min(concurrency, ids.length) }, worker));
	if (stats) stats.started = next;
	return count;
}
