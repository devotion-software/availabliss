/**
 * Incoming change notifications. Each one is checked against the token we gave
 * its channel, then the calendar's connection is synced shortly after: a burst of
 * notifications (providers send several per edit) collapses into one sync, and
 * one that lands while that sync runs makes it go round again.
 */
import { eq, inArray } from 'drizzle-orm';
import { db } from '../db';
import { calendars } from '../db/schema';
import { kv } from '../redis';
import { syncConnection } from './index';
import { verifyPushToken } from './push';

/** Wait this long after the first notification, so the rest of a burst is included (tests shorten it). */
export const pushTiming = { debounceMs: 3000 };
/** At most this many syncs back to back for one run of notifications. */
const MAX_ROUNDS = 3;
/** Changed Outlook event ids kept for one sync; past this, assume the far tier changed. */
const MAX_EVENT_IDS = 50;

const runningKey = (connectionId: string) => `calendar-push:running:${connectionId}`;
const lastKey = (connectionId: string) => `calendar-push:last:${connectionId}`;
const eventsKey = (connectionId: string) => `calendar-push:events:${connectionId}`;

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Notes that a connection changed and syncs it soon (on this instance, once across
 * instances). `eventIds`: the changed events, where the provider names them.
 */
export async function requestSync(connectionId: string, eventIds: (string | null)[] = []): Promise<void> {
	if (eventIds.length) {
		const known = JSON.parse((await kv.get(eventsKey(connectionId))) ?? '[]') as (string | null)[];
		// Too many to look up one by one: an unknown id makes the sync re-read everything.
		const merged = known.length + eventIds.length > MAX_EVENT_IDS ? [null] : [...known, ...eventIds];
		await kv.set(eventsKey(connectionId), JSON.stringify(merged), 15 * 60);
	}
	await kv.set(lastKey(connectionId), String(Date.now()), 15 * 60);
	if (!(await kv.setIfAbsent(runningKey(connectionId), '1', 5 * 60))) return;
	void runSyncs(connectionId);
}

async function runSyncs(connectionId: string): Promise<void> {
	try {
		for (let round = 0; round < MAX_ROUNDS; round++) {
			await sleep(pushTiming.debounceMs);
			const startedAt = Date.now();
			const events = JSON.parse((await kv.take(eventsKey(connectionId))) ?? '[]') as (string | null)[];
			await syncConnection(connectionId, { pushedEvents: events });
			// Anything that arrived after this sync started needs another.
			if (Number((await kv.get(lastKey(connectionId))) ?? 0) < startedAt) break;
		}
	} catch (err) {
		console.warn('[calendars] push sync failed', connectionId, err);
	} finally {
		await kv.delete(runningKey(connectionId));
	}
}

/**
 * Google: one POST per change to a watched calendar, described only by headers
 * (`X-Goog-Channel-ID`, `X-Goog-Channel-Token`, `X-Goog-Resource-State`). The first
 * message on a new channel has state `sync` and means nothing changed.
 */
export async function onGoogleNotification(headers: Headers): Promise<void> {
	const channelId = headers.get('x-goog-channel-id');
	if (!channelId || headers.get('x-goog-resource-state') === 'sync') return;
	const cal = await db.query.calendars.findFirst({ where: eq(calendars.pushId, channelId) });
	if (!cal || !verifyPushToken(cal.id, headers.get('x-goog-channel-token'))) return;
	await requestSync(cal.connectionId);
}

export interface GraphNotification {
	subscriptionId?: string;
	clientState?: string;
	changeType?: string;
	resourceData?: { id?: string };
}

/** Microsoft Graph: a batch of notifications, each naming its subscription and the changed event. */
export async function onMicrosoftNotifications(body: { value?: GraphNotification[] } | null): Promise<void> {
	const items = (body?.value ?? []).filter((n) => n.subscriptionId);
	if (!items.length) return;
	const rows = await db
		.select({ id: calendars.id, connectionId: calendars.connectionId, pushId: calendars.pushId })
		.from(calendars)
		.where(inArray(calendars.pushId, [...new Set(items.map((n) => n.subscriptionId!))]));
	const bySubscription = new Map(rows.map((r) => [r.pushId!, r]));

	const byConnection = new Map<string, (string | null)[]>();
	for (const n of items) {
		const cal = bySubscription.get(n.subscriptionId!);
		if (!cal || !verifyPushToken(cal.id, n.clientState)) continue;
		// A deleted event can't be looked up afterwards, so its id doesn't help.
		const eventId = n.changeType === 'deleted' ? null : (n.resourceData?.id ?? null);
		byConnection.set(cal.connectionId, [...(byConnection.get(cal.connectionId) ?? []), eventId]);
	}
	for (const [connectionId, eventIds] of byConnection) await requestSync(connectionId, eventIds);
}
