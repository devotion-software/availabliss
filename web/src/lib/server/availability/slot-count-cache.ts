/**
 * The groups list's "12 weekends work" count runs the whole availability engine
 * (every member's busy time, holidays) per group, so it's cached in the shared
 * store. Busy time, timing and membership changes clear it (see realtime.ts);
 * the stamp also changes with the group row and the day, and a short TTL bounds
 * anything that slips through (a member changing region, holiday imports).
 */
import { kv } from '../redis';
import { today } from '../requirements';
import type { GroupRow } from '../groups';
import { upcomingSlotCount } from './service';

const TTL_SECONDS = 10 * 60;
const key = (groupId: string) => `slots:${groupId}`;
const stampOf = (group: GroupRow) => `${today(group.requirement.timezone)}|${group.updatedAt.getTime()}`;

export async function cachedSlotCount(group: GroupRow): Promise<number> {
	const stamp = stampOf(group);
	try {
		const hit = await kv.get(key(group.id));
		if (hit) {
			const [cachedStamp, count] = hit.split('#');
			if (cachedStamp === stamp) return Number(count);
		}
	} catch {
		// The store being down only costs a recompute.
	}
	const count = await upcomingSlotCount(group);
	await kv.set(key(group.id), `${stamp}#${count}`, TTL_SECONDS).catch(() => {});
	return count;
}

export async function clearSlotCounts(groupIds: Iterable<string>): Promise<void> {
	await Promise.all([...groupIds].map((id) => kv.delete(key(id)).catch(() => {})));
}
