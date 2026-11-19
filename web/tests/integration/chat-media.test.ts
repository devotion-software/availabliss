import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import sharp from 'sharp';
import { createGroup, deleteGroup } from '#lib/server/groups';
import { deleteMessage, sendText } from '#lib/server/chat';
import { listGroupMedia, uploadAttachment } from '#lib/server/attachments';
import { makePass } from '#lib/server/wallet-pass.fixture';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('chat-media');
// Through deleteGroup, so the stored files go too.
afterAll(async () => {
	await deleteGroup(groupId, owner);
	await fx.cleanup();
});

let owner: string, member: string, outsider: string, groupId: string;

beforeAll(async () => {
	owner = await fx.user({ name: 'Ola' });
	member = await fx.user({ name: 'Mo' });
	outsider = await fx.user();
	groupId = (await createGroup(owner, { name: 'Holiday', requirement: everyDay() }, 'UTC')).id;
	await fx.join(groupId, member);
});

const photo = async (name: string) =>
	new File(
		[
			await sharp({ create: { width: 40, height: 30, channels: 3, background: '#78b4ff' } })
				.jpeg()
				.toBuffer()
		],
		name,
		{ type: 'image/jpeg' }
	);
const pdf = (name: string) => new File(['%PDF-1.4 tiny'], name, { type: 'application/pdf' });
const send = async (userId: string, files: File[], caption = '') => {
	const ids = [];
	for (const f of files) ids.push((await uploadAttachment(groupId, userId, f)).id);
	return sendText(groupId, userId, caption, null, ids);
};
const names = (page: Awaited<ReturnType<typeof listGroupMedia>>) => page.items.map((i) => i.attachment.name);

describe("a chat's media page", () => {
	test('photos and files apart, newest message first and in order within one, paged', async () => {
		const first = await send(owner, [await photo('a.jpg'), await photo('b.jpg')]);
		await send(member, [pdf('Plan.pdf')], 'The plan');
		await send(member, [await photo('c.jpg')]);
		const gone = await send(owner, [await photo('gone.jpg')]);
		await deleteMessage(gone.id, owner);
		// Uploaded but never sent.
		await uploadAttachment(groupId, owner, await photo('unsent.jpg'));

		const media = await listGroupMedia(groupId, member, 'media', null, 60);
		expect(names(media)).toEqual(['c.jpg', 'a.jpg', 'b.jpg']);
		expect(media.hasMore).toBe(false);
		// No passes sent, so no Passes tab.
		expect(media.hasPasses).toBe(false);
		expect((await listGroupMedia(groupId, member, 'passes', null, 60)).items).toEqual([]);
		expect(media.items[1]).toMatchObject({ messageId: first.id, authorId: owner, createdAt: first.createdAt });

		expect(names(await listGroupMedia(groupId, member, 'files', null, 60))).toEqual(['Plan.pdf']);

		const page1 = await listGroupMedia(groupId, member, 'media', null, 2);
		expect(names(page1)).toEqual(['c.jpg', 'a.jpg']);
		expect(page1.hasMore).toBe(true);
		const page2 = await listGroupMedia(groupId, member, 'media', page1.items[1].attachment.id, 2);
		expect(names(page2)).toEqual(['b.jpg']);
		expect(page2.hasMore).toBe(false);
		// Only the first page says whether there are passes.
		expect(page2.hasPasses).toBeUndefined();
	});

	test('Wallet passes have their own tab, apart from files', async () => {
		await send(member, [new File([makePass()], 'gig.pkpass', { type: 'application/vnd.apple.pkpass' })]);
		const passes = await listGroupMedia(groupId, member, 'passes', null, 60);
		expect(names(passes)).toEqual(['gig.pkpass']);
		expect(passes.items[0].attachment.kind).toBe('pass');
		expect(passes.hasPasses).toBe(true);
		expect(names(await listGroupMedia(groupId, member, 'files', null, 60))).not.toContain('gig.pkpass');
		expect((await listGroupMedia(groupId, member, 'media', null, 60)).hasPasses).toBe(true);
	});

	test('only members, and only an attachment from this group to page from', async () => {
		expect((await apiError(listGroupMedia(groupId, outsider, 'media', null, 60))).code).toBe('not_found');
		expect((await apiError(listGroupMedia(groupId, member, 'media', 'nope', 60))).code).toBe('not_found');
	});
});
