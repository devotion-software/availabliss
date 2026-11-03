/**
 * Pub/sub behind GET /api/v1/live. Each signed-in user has listeners on the
 * instance holding their SSE connection; mutations publish to the users who
 * should see them. Events travel over the shared bus (Redis pub/sub when
 * configured), so a change made on one instance reaches connections on all of them.
 */
import type { LiveEvent } from '#lib/types';
import { bus as sharedBus, type Bus } from './redis';
import { memberIds } from './groups';
import { eq } from 'drizzle-orm';
import { alias } from 'drizzle-orm/mysql-core';
import { db } from './db';
import { groupMembers } from './db/schema';

const mine = alias(groupMembers, 'mine');
const theirs = alias(groupMembers, 'theirs');

type Listener = (event: LiveEvent) => void;

const CHANNEL = 'live';

interface Envelope {
	userIds: string[];
	event: LiveEvent;
}

/** Local listeners plus the bus that fans events out across instances. Exported for tests. */
export function createHub(bus: Bus) {
	const channels = new Map<string, Set<Listener>>();
	let ready: Promise<void> | null = null;

	function deliver(raw: string) {
		let envelope: Envelope;
		try {
			envelope = JSON.parse(raw);
		} catch {
			return;
		}
		for (const userId of envelope.userIds) {
			for (const listener of channels.get(userId) ?? []) {
				try {
					listener(envelope.event);
				} catch {
					// A broken connection shouldn't affect the others.
				}
			}
		}
	}

	return {
		subscribe(userId: string, listener: Listener): () => void {
			ready ??= bus.subscribe(CHANNEL, deliver).catch((err) => {
				ready = null;
				console.warn('[live] could not subscribe to the bus', err);
			});
			let set = channels.get(userId);
			if (!set) channels.set(userId, (set = new Set()));
			set.add(listener);
			return () => {
				set.delete(listener);
				if (set.size === 0) channels.delete(userId);
			};
		},

		publishToUsers(userIds: Iterable<string>, event: LiveEvent): Promise<void> {
			const envelope: Envelope = { userIds: [...new Set(userIds)], event };
			if (!envelope.userIds.length) return Promise.resolve();
			return bus.publish(CHANNEL, JSON.stringify(envelope)).catch((err) => {
				// Live updates are best effort; clients also refresh on focus.
				console.warn('[live] publish failed', err);
			});
		}
	};
}

const hub = createHub(sharedBus);

export const subscribe = hub.subscribe;

export function publishToUsers(userIds: Iterable<string>, event: LiveEvent): void {
	void hub.publishToUsers(userIds, event);
}

/** Sends an event to every member of a group. */
export async function publishToGroup(groupId: string, type: LiveEvent['type'], payload: unknown): Promise<void> {
	publishToUsers(await memberIds(groupId), { type, groupId, payload });
}

/** A user's busy time changed: every group they're in should refresh availability. */
export async function publishAvailabilityChanged(userId: string): Promise<void> {
	// One query for every group's members, then publish per group.
	const rows = await db
		.select({ groupId: mine.groupId, memberId: theirs.userId })
		.from(mine)
		.innerJoin(theirs, eq(theirs.groupId, mine.groupId))
		.where(eq(mine.userId, userId));
	const byGroup = new Map<string, string[]>();
	for (const r of rows) byGroup.set(r.groupId, [...(byGroup.get(r.groupId) ?? []), r.memberId]);
	for (const [groupId, members] of byGroup) {
		publishToUsers(members, { type: 'availability.changed', groupId, payload: { userId } });
	}
}
