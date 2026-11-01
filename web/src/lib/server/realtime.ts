/**
 * In-process pub/sub behind GET /api/v1/live. Each signed-in user has a channel;
 * mutations publish to the users who should see them. Single-node only (swap for
 * Redis pub/sub to scale horizontally).
 */
import type { LiveEvent } from '#lib/types';
import { groupIdsFor, memberIds } from './groups';

type Listener = (event: LiveEvent) => void;

const channels = new Map<string, Set<Listener>>();

export function subscribe(userId: string, listener: Listener): () => void {
	let set = channels.get(userId);
	if (!set) channels.set(userId, (set = new Set()));
	set.add(listener);
	return () => {
		set.delete(listener);
		if (set.size === 0) channels.delete(userId);
	};
}

export function publishToUsers(userIds: Iterable<string>, event: LiveEvent): void {
	for (const userId of userIds) {
		for (const listener of channels.get(userId) ?? []) {
			try {
				listener(event);
			} catch {
				// A broken connection shouldn't affect the others.
			}
		}
	}
}

/** Sends an event to every member of a group. */
export async function publishToGroup(groupId: string, type: LiveEvent['type'], payload: unknown): Promise<void> {
	publishToUsers(await memberIds(groupId), { type, groupId, payload });
}

/** A user's busy time changed: every group they're in should refresh availability. */
export async function publishAvailabilityChanged(userId: string): Promise<void> {
	for (const groupId of await groupIdsFor(userId)) await publishToGroup(groupId, 'availability.changed', { userId });
}
