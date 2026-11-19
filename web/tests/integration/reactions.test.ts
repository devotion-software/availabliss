import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import { createGroup } from '#lib/server/groups';
import { deleteMessage, listMessages, postMessage, removeReaction, sendText, setReaction } from '#lib/server/chat';
import { everyDay, fixture } from './fixtures';

const fx = fixture('reactions');
afterAll(() => fx.cleanup());

let me: string, friend: string, other: string, group: string;

beforeAll(async () => {
	me = await fx.user({ name: 'Sam' });
	friend = await fx.user({ name: 'Mo' });
	other = await fx.user({ name: 'Kim' });
	group = (await createGroup(me, { name: 'Reacts', requirement: everyDay() }, 'UTC')).id;
	await fx.join(group, friend);
	await fx.join(group, other);
});

describe('reactions', () => {
	test('one per person, grouped by emoji in the order they came, and in the message', async () => {
		const m = await sendText(group, me, 'Pizza tonight?');
		expect(m.reactions).toEqual([]);
		await setReaction(m.id, friend, '👍');
		await Bun.sleep(5);
		await setReaction(m.id, other, '❤️');
		await Bun.sleep(5);
		const res = await setReaction(m.id, me, '👍');
		expect(res).toEqual({
			messageId: m.id,
			reactions: [
				{ emoji: '👍', userIds: [friend, me] },
				{ emoji: '❤️', userIds: [other] }
			]
		});

		// Reacting again replaces your reaction (and counts from then).
		await Bun.sleep(5);
		const changed = await setReaction(m.id, friend, '❤️');
		expect(changed.reactions).toEqual([
			{ emoji: '❤️', userIds: [other, friend] },
			{ emoji: '👍', userIds: [me] }
		]);

		const listed = (await listMessages(group, other, null, 50)).messages.find((x) => x.id === m.id)!;
		expect(listed.reactions).toEqual(changed.reactions);

		expect((await removeReaction(m.id, friend)).reactions).toEqual([
			{ emoji: '❤️', userIds: [other] },
			{ emoji: '👍', userIds: [me] }
		]);
		// Taking back a reaction you don't have changes nothing.
		expect((await removeReaction(m.id, friend)).reactions).toHaveLength(2);
	});

	test('not on group updates or deleted messages, and only for members; deleting clears them', async () => {
		const sys = await postMessage(group, null, { kind: 'system', body: 'Mo joined' });
		expect(setReaction(sys.id, me, '👍')).rejects.toThrow();
		const m = await sendText(group, friend, 'Oops');
		await setReaction(m.id, me, '😂');
		await deleteMessage(m.id, friend);
		const listed = (await listMessages(group, me, null, 50)).messages.find((x) => x.id === m.id)!;
		expect(listed.reactions).toEqual([]);
		expect(setReaction(m.id, me, '👍')).rejects.toThrow('deleted');
		const stranger = await fx.user();
		const fresh = await sendText(group, me, 'Hi');
		expect(setReaction(fresh.id, stranger, '👍')).rejects.toThrow();
	});
});
