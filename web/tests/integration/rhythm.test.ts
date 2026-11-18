import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { and, eq } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { messages } from '#lib/server/db/schema';
import { createGroup, updateGroup } from '#lib/server/groups';
import { createEvent } from '#lib/server/events';
import { nextSuggestion, promptNextPlans } from '#lib/server/rhythm';
import { everyDay, fixture } from './fixtures';

const fx = fixture('rhythm');
afterAll(() => fx.cleanup());

describe('rhythm', () => {
	test('suggests the next time near the rhythm and prompts once after a plan', async () => {
		const [owner, member] = await fx.users(2);
		const groupId = (await createGroup(owner, { name: 'Monthly', requirement: everyDay('2032-01-01', '2032-12-31') }, 'UTC')).id;
		await fx.join(groupId, member);
		expect(await nextSuggestion(groupId, member)).toBeNull();

		await updateGroup(groupId, owner, { rhythm: { every: 'month', prompt: true } });
		await createEvent(groupId, owner, {
			title: 'Supper',
			start: '2032-03-05T18:00:00.000Z',
			end: '2032-03-05T21:00:00.000Z',
			allDay: false,
			location: null,
			note: null
		});
		const now = Date.parse('2032-03-06T09:00:00Z');
		const suggestion = await nextSuggestion(groupId, member, now);
		expect(suggestion?.target).toBe('2032-04-02');
		expect(suggestion?.slot?.start.slice(0, 10)).toBe('2032-04-02');

		await promptNextPlans(now);
		await promptNextPlans(now + 300_000);
		const posted = await db
			.select()
			.from(messages)
			.where(and(eq(messages.groupId, groupId), eq(messages.kind, 'system')));
		const prompts = posted.filter((m) => m.body?.startsWith('Same again?'));
		expect(prompts).toHaveLength(1);
		expect(prompts[0].body).toContain('works for everyone');
	});
});
