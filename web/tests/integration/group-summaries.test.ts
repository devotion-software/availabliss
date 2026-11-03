import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { createGroup } from '#lib/server/groups';
import { groupSummaries } from '#lib/server/group-summaries';
import { createBlock } from '#lib/server/blocks';
import { publishAvailabilityChanged, publishToGroup } from '#lib/server/realtime';
import { createProposal } from '#lib/server/proposals';
import { everyDay, fixture } from './fixtures';

const fx = fixture('summaries');
afterAll(() => fx.cleanup());

const status = async (userId: string, groupId: string) => (await groupSummaries(userId)).find((g) => g.id === groupId)!.status;

describe('groupSummaries', () => {
	test('cached slot counts clear when busy time or membership changes', async () => {
		const [me, friend] = await fx.users(2);
		const groupId = (await createGroup(me, { name: 'Every day', requirement: everyDay() }, 'UTC')).id;
		await fx.join(groupId, friend);

		expect(await status(me, groupId)).toMatchObject({ kind: 'matches', count: 7 });

		// Busy one day: the cached 7 is cleared by availability.changed.
		await createBlock(friend, { start: '2031-03-05T00:00:00Z', end: '2031-03-06T00:00:00Z', allDay: false, repeat: 'none', until: null, note: null });
		expect(await status(me, groupId)).toMatchObject({ count: 7 }); // still cached
		await publishAvailabilityChanged(friend);
		expect(await status(me, groupId)).toMatchObject({ kind: 'matches', count: 6 });

		// A new member's busy time counts once member.changed clears it.
		const third = await fx.user();
		await createBlock(third, { start: '2031-03-07T00:00:00Z', end: '2031-03-08T00:00:00Z', allDay: false, repeat: 'none', until: null, note: null });
		await fx.join(groupId, third);
		await publishToGroup(groupId, 'member.changed', {});
		expect(await status(me, groupId)).toMatchObject({ kind: 'matches', count: 5 });
	});

	test('batched badges: needs reply, unread and members per group', async () => {
		const [me, friend] = await fx.users(2);
		const a = (await createGroup(me, { name: 'A', requirement: everyDay() }, 'UTC')).id;
		const b = (await createGroup(me, { name: 'B', requirement: everyDay() }, 'UTC')).id;
		await fx.join(a, friend);
		const p = await createProposal(a, friend, {
			start: '2031-03-04T18:00:00.000Z',
			end: '2031-03-04T21:00:00.000Z',
			allDay: false,
			title: null,
			location: null,
			note: null
		});
		const list = await groupSummaries(me);
		const byId = Object.fromEntries(list.map((g) => [g.id, g]));
		expect(byId[a]).toMatchObject({ memberCount: 2, status: { kind: 'needs_reply', proposalId: p.id }, unreadCount: 1 });
		expect(byId[b]).toMatchObject({ memberCount: 1, unreadCount: 0, status: { kind: 'matches' } });
		expect(byId[a].memberPreview.map((m) => m.userId)).toEqual([me, friend]);
	});
});
