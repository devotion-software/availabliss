import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import { and, eq } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { messages, notifications } from '#lib/server/db/schema';
import { createGroup, getGroup, memberName, setMemberDisplayName } from '#lib/server/groups';
import { groupSummaries } from '#lib/server/group-summaries';
import { listMessages, sendText } from '#lib/server/chat';
import { createProposal, respond } from '#lib/server/proposals';
import { memberNameSchema } from '#lib/schemas';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('member-names');
afterAll(() => fx.cleanup());

describe('the name you go by in a group', () => {
	let owner: string, sam: string, outsider: string, groupId: string, otherGroupId: string;

	beforeAll(async () => {
		owner = await fx.user({ name: 'Ada Lovelace' });
		sam = await fx.user({ name: 'Samuel Lee' });
		outsider = await fx.user();
		groupId = (await createGroup(owner, { name: 'Climbing', requirement: everyDay() }, 'UTC')).id;
		otherGroupId = (await createGroup(owner, { name: 'Work', requirement: everyDay() }, 'UTC')).id;
		await fx.join(groupId, sam);
		await fx.join(otherGroupId, sam);
	});

	test('validation: trimmed, at most 80 characters, blank clears', () => {
		expect(memberNameSchema.parse({ displayName: '  Sammy ' })).toEqual({ displayName: 'Sammy' });
		expect(memberNameSchema.parse({ displayName: '   ' })).toEqual({ displayName: null });
		expect(memberNameSchema.parse({ displayName: null })).toEqual({ displayName: null });
		expect(memberNameSchema.safeParse({ displayName: 'x'.repeat(81) }).success).toBe(false);
		expect(memberNameSchema.safeParse({}).success).toBe(false);
	});

	test('setting it renames you in that group only; clearing it goes back to your account name', async () => {
		const member = await setMemberDisplayName(groupId, sam, 'Sammy');
		expect(member).toMatchObject({ userId: sam, name: 'Sammy', nameInGroup: 'Sammy' });
		expect((await getGroup(groupId, owner)).members.find((m) => m.userId === sam)).toMatchObject({ name: 'Sammy', nameInGroup: 'Sammy' });
		expect((await getGroup(otherGroupId, owner)).members.find((m) => m.userId === sam)).toMatchObject({ name: 'Samuel Lee', nameInGroup: null });
		expect(await memberName(groupId, sam)).toBe('Sammy');
		expect(await memberName(otherGroupId, sam)).toBe('Samuel Lee');

		const summaries = await groupSummaries(owner);
		const summary = summaries.find((g) => g.id === groupId)!;
		expect(summary.memberPreview.find((m) => m.userId === sam)?.name).toBe('Sammy');

		const cleared = await setMemberDisplayName(groupId, sam, null);
		expect(cleared).toMatchObject({ name: 'Samuel Lee', nameInGroup: null });
		await setMemberDisplayName(groupId, sam, 'Sammy');
	});

	test('only members can set one', async () => {
		expect((await apiError(setMemberDisplayName(groupId, outsider, 'Nope'))).code).toBe('not_found');
	});

	test('reply quotes and pushes use it', async () => {
		const first = await sendText(groupId, sam, 'Who is in for Saturday?');
		await sendText(groupId, owner, 'Me!', first.id);
		const { messages: page } = await listMessages(groupId, owner, null, 10);
		expect(page.find((m) => m.replyTo?.id === first.id)?.replyTo?.authorName).toBe('Sammy');
		const pushed = await db
			.select()
			.from(notifications)
			.where(and(eq(notifications.groupId, groupId), eq(notifications.userId, owner), eq(notifications.kind, 'message')));
		expect(pushed.some((n) => n.body.startsWith('Sammy:'))).toBe(true);
	});

	test('proposal pushes and system messages use it', async () => {
		const p = await createProposal(groupId, sam, {
			start: '2031-03-05T18:00:00.000Z',
			end: '2031-03-05T21:00:00.000Z',
			allDay: false,
			title: null,
			location: null,
			note: null
		});
		const told = await db
			.select()
			.from(notifications)
			.where(and(eq(notifications.groupId, groupId), eq(notifications.userId, owner), eq(notifications.kind, 'proposal.created')));
		expect(told[0]?.body).toStartWith('Sammy proposed');

		await respond(p.id, owner, 'accepted'); // everyone's in: confirmed
		await respond(p.id, sam, 'declined'); // drops out
		const system = await db
			.select()
			.from(messages)
			.where(and(eq(messages.groupId, groupId), eq(messages.kind, 'system')));
		expect(system.map((m) => m.body)).toContain("Sammy can't make it any more");
	});
});
