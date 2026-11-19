import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import { createGroup, updateGroup } from '#lib/server/groups';
import { deleteMessage, markReceipts, sendPoll, sendText } from '#lib/server/chat';
import { listChats } from '#lib/server/chats';
import { everyDay, fixture } from './fixtures';

const fx = fixture('chats');
afterAll(() => fx.cleanup());

let me: string, friend: string, quiet: string, busy: string, archived: string;

beforeAll(async () => {
	me = await fx.user({ name: 'Sam' });
	friend = await fx.user({ name: 'Mo' });
	quiet = (await createGroup(me, { name: 'Quiet', requirement: everyDay() }, 'UTC')).id;
	busy = (await createGroup(me, { name: 'Busy', requirement: everyDay() }, 'UTC')).id;
	archived = (await createGroup(me, { name: 'Old', requirement: everyDay() }, 'UTC')).id;
	for (const g of [quiet, busy, archived]) await fx.join(g, friend);
});

describe('the chats list', () => {
	test('latest message first, with a preview, who sent it and how many are unread', async () => {
		await sendText(quiet, friend, 'Anyone around?');
		await sendText(busy, me, 'First');
		const last = await sendText(busy, friend, 'Second, and a much longer line');
		await sendPoll(quiet, me, { question: 'Pizza?', options: ['Yes', 'No'], multi: false });

		const chats = await listChats(me);
		const names = chats.map((c) => c.group.name);
		expect(names.indexOf('Quiet')).toBeLessThan(names.indexOf('Busy'));
		const q = chats.find((c) => c.group.id === quiet)!;
		expect(q.lastMessage).toMatchObject({ authorId: me, kind: 'poll', text: 'Pizza?' });
		expect(q.unreadCount).toBe(1);
		const b = chats.find((c) => c.group.id === busy)!;
		expect(b.lastMessage).toMatchObject({ id: last.id, authorName: 'Mo', text: 'Second, and a much longer line' });

		await markReceipts(busy, me, last.id, 'seen');
		expect((await listChats(me)).find((c) => c.group.id === busy)!.unreadCount).toBe(0);
	});

	test('a deleted last message shows as deleted; archived groups only while unread', async () => {
		const gone = await sendText(busy, me, 'Oops');
		await deleteMessage(gone.id, me);
		expect((await listChats(me)).find((c) => c.group.id === busy)!.lastMessage).toMatchObject({ deleted: true, text: null });

		await updateGroup(archived, me, { archived: true });
		expect((await listChats(me)).some((c) => c.group.id === archived)).toBe(false);
		await sendText(archived, friend, 'Still here?');
		expect((await listChats(me)).find((c) => c.group.id === archived)?.unreadCount).toBe(1);
		// Not in someone else's list.
		const stranger = await fx.user();
		expect(await listChats(stranger)).toEqual([]);
	});
});
