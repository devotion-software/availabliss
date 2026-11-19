import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { createGroup, updateGroup } from '#lib/server/groups';
import { publishGroupUpdated, subscribe } from '#lib/server/realtime';
import type { Group, LiveEvent } from '#lib/types';
import { everyDay, fixture } from './fixtures';

const fx = fixture('liveupdated');
afterAll(() => fx.cleanup());

describe('group.updated', () => {
	test("an admin's change reaches each member with their own role", async () => {
		const [owner, member] = await fx.users(2);
		const groupId = (await createGroup(owner, { name: 'Roles', requirement: everyDay() }, 'UTC')).id;
		await fx.join(groupId, member);

		const seen = new Map<string, Group>();
		const listen = (userId: string) =>
			subscribe(userId, (e: LiveEvent) => {
				if (e.type === 'group.updated' && e.groupId === groupId) seen.set(userId, e.payload as Group);
			});
		const off = [listen(owner), listen(member)];
		try {
			// Lets the bus subscription settle before publishing.
			await Bun.sleep(200);
			await publishGroupUpdated(await updateGroup(groupId, owner, { name: 'Roles, renamed' }));
			for (let i = 0; i < 40 && seen.size < 2; i++) await Bun.sleep(25);
			expect(seen.get(owner)).toMatchObject({ name: 'Roles, renamed', role: 'owner' });
			expect(seen.get(member)).toMatchObject({ name: 'Roles, renamed', role: 'member' });
		} finally {
			off.forEach((stop) => stop());
		}
	});
});
