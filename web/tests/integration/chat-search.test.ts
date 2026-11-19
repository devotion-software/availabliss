import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import { createGroup, deleteGroup } from '#lib/server/groups';
import { deleteMessage, searchMessages, sendLocation, sendPoll, sendText } from '#lib/server/chat';
import { uploadAttachment } from '#lib/server/attachments';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('chat-search');
// Through deleteGroup, so the stored file goes too.
afterAll(async () => {
	await deleteGroup(groupId, owner);
	await fx.cleanup();
});

let owner: string, member: string, outsider: string, groupId: string, otherGroup: string;

beforeAll(async () => {
	owner = await fx.user({ name: 'Ola' });
	member = await fx.user({ name: 'Mo' });
	outsider = await fx.user();
	groupId = (await createGroup(owner, { name: 'Pub quiz', requirement: everyDay() }, 'UTC')).id;
	otherGroup = (await createGroup(owner, { name: 'Elsewhere', requirement: everyDay() }, 'UTC')).id;
	await fx.join(groupId, member);
});

const ids = async (q: string, limit = 50) => (await searchMessages(groupId, member, q, limit)).messages.map((m) => m.id);

describe('searching the chat', () => {
	test('finds text, captions, polls, file names and places, any case, newest first', async () => {
		const text = await sendText(groupId, owner, 'Shall we try the Crown on Friday?');
		const poll = await sendPoll(groupId, member, { question: 'Which pub?', options: ['The Crown', 'Red Lion'], multi: false });
		const option = await sendPoll(groupId, member, { question: 'Starter?', options: ['Nachos', 'crown fries'], multi: false });
		const file = await uploadAttachment(groupId, owner, new File(['%PDF-1.4 tiny'], 'Crown menu.pdf', { type: 'application/pdf' }));
		const withFile = await sendText(groupId, owner, '', null, [file.id]);
		const place = await sendLocation(groupId, member, { lat: 51.5, lng: -0.1, name: 'The Crown', address: '1 High St', accuracy: null });
		await sendText(groupId, owner, 'Something unrelated');
		await sendText(otherGroup, owner, 'The Crown again');

		expect(await ids('CROWN')).toEqual([place.id, withFile.id, option.id, poll.id, text.id]);
		expect(await ids('high st')).toEqual([place.id]);
		expect(await ids('crown', 2)).toEqual([place.id, withFile.id]);
		expect((await searchMessages(groupId, member, 'crown', 2)).hasMore).toBe(true);
	});

	test('% and _ match literally', async () => {
		const percent = await sendText(groupId, owner, 'Only 50% off');
		await sendText(groupId, owner, 'Only 500 off');
		expect(await ids('50%')).toEqual([percent.id]);
		const under = await sendText(groupId, owner, 'my_file');
		await sendText(groupId, owner, 'myXfile');
		expect(await ids('y_f')).toEqual([under.id]);
	});

	test("deleted messages and group updates aren't found; only members can search", async () => {
		const gone = await sendText(groupId, owner, 'Secret plan');
		await deleteMessage(gone.id, owner);
		expect(await ids('secret plan')).toEqual([]);
		expect(await ids('joined')).toEqual([]);
		expect((await apiError(searchMessages(groupId, outsider, 'crown', 50))).code).toBe('not_found');
	});
});
