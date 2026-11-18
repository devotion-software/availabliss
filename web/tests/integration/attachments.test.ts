import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import { eq } from 'drizzle-orm';
import sharp from 'sharp';
import { db } from '#lib/server/db';
import { messageAttachments } from '#lib/server/db/schema';
import { createGroup, deleteGroup } from '#lib/server/groups';
import { deleteMessage, editMessage, listMessages, messageThread, sendText } from '#lib/server/chat';
import { deleteAbandonedUploads, PENDING_TTL_MS, serveAttachment, uploadAttachment } from '#lib/server/attachments';
import { getObject } from '#lib/server/media';
import { listNotifications } from '#lib/server/notifications';
import { makePass } from '#lib/server/wallet-pass.fixture';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('attachments');
// Through deleteGroup, so the stored files go too.
afterAll(async () => {
	await deleteGroup(groupId, owner);
	await fx.cleanup();
});

let owner: string, member: string, outsider: string, groupId: string;

const photo = async () => new File([await sharp({ create: { width: 3000, height: 1500, channels: 3, background: '#78b4ff' } }).jpeg().toBuffer()], 'IMG_0001.HEIC', { type: 'image/heic' });
const gif = async () => {
	const frame = (colour: string) => sharp({ create: { width: 40, height: 30, channels: 3, background: colour } }).png().toBuffer();
	const bytes = await sharp([await frame('#f00'), await frame('#00f')], { join: { animated: true } }).gif().toBuffer();
	return new File([bytes], 'dance.gif', { type: 'image/gif' });
};
const pdf = () => new File(['%PDF-1.4 tiny'], 'Plan.pdf', { type: 'application/pdf' });
const keysOf = async (id: string) => (await db.query.messageAttachments.findFirst({ where: eq(messageAttachments.id, id) }))!;
const fetchAs = (url: string, userId: string | null) => serveAttachment(new URL(url).pathname.split('/')[4], url.includes('/thumb?') ? 'thumb' : 'file', new URL(url), userId, null);

beforeAll(async () => {
	owner = await fx.user({ name: 'Ola' });
	[member, outsider] = await fx.users(2);
	groupId = (await createGroup(owner, { name: 'Holiday snaps', requirement: everyDay() }, 'UTC')).id;
	await fx.join(groupId, member);
});

describe('uploads', () => {
	test('photos are resized with a preview; GIFs stay animated; files keep their name', async () => {
		const p = await uploadAttachment(groupId, member, await photo());
		expect(p).toMatchObject({ kind: 'image', name: 'IMG_0001.jpg', mime: 'image/jpeg', width: 2560, height: 1280 });
		expect(p.thumbnailUrl).toContain(`/api/v1/attachments/${p.id}/thumb?`);
		const thumb = await getObject((await keysOf(p.id)).thumbKey!);
		expect((await sharp(thumb!.body as Uint8Array).metadata()).width).toBe(720);

		const g = await uploadAttachment(groupId, member, await gif());
		expect(g).toMatchObject({ kind: 'gif', mime: 'image/gif', width: 40, height: 30, thumbnailUrl: null });

		const f = await uploadAttachment(groupId, member, pdf());
		expect(f).toMatchObject({ kind: 'file', name: 'Plan.pdf', mime: 'application/pdf', size: 13, width: null });
	});

	test('only members, only allowed types, nothing empty', async () => {
		expect((await apiError(uploadAttachment(groupId, outsider, pdf()))).code).toBe('not_found');
		expect((await apiError(uploadAttachment(groupId, member, new File(['MZ'], 'setup.exe'))))).toMatchObject({ code: 'validation_error' });
		expect((await apiError(uploadAttachment(groupId, member, new File(['<svg/>'], 'x.svg', { type: 'image/svg+xml' }))))).toMatchObject({ code: 'validation_error' });
		expect((await apiError(uploadAttachment(groupId, member, new File(['not a photo'], 'x.jpg'))))).toMatchObject({ code: 'validation_error' });
		expect((await apiError(uploadAttachment(groupId, member, new File([], 'x.pdf'))))).toMatchObject({ code: 'validation_error' });
	});
});

describe('messages with attachments', () => {
	test('sent in order with a summary for older apps; quoted and pushed by summary', async () => {
		const p = await uploadAttachment(groupId, member, await photo());
		const f = await uploadAttachment(groupId, member, pdf());
		const sent = await sendText(groupId, member, null, null, [f.id, p.id]);
		expect(sent.attachments.map((a) => a.id)).toEqual([f.id, p.id]);
		expect(sent).toMatchObject({ kind: 'text', body: '📎 2 attachments', caption: null });

		const reply = await sendText(groupId, owner, 'Nice', sent.id);
		expect(reply.replyTo?.text).toBe('📎 2 attachments');
		expect((await messageThread(reply.id, owner)).messages[0].attachments).toHaveLength(2);

		const g = await uploadAttachment(groupId, member, await gif());
		const captioned = await sendText(groupId, member, 'Us on the beach', null, [g.id]);
		expect(captioned).toMatchObject({ body: 'Us on the beach', caption: 'Us on the beach' });
		const { notifications } = await listNotifications(owner);
		expect(notifications.find((n) => n.kind === 'message' && n.groupId === groupId)?.body).toContain('Us on the beach');

		const listed = (await listMessages(groupId, owner, null, 50)).messages;
		expect(listed.find((m) => m.id === sent.id)?.attachments).toHaveLength(2);
		expect(listed.find((m) => m.id === reply.id)?.attachments).toEqual([]);
	});

	test("uploads can't be reused, borrowed or moved to another group", async () => {
		const p = await uploadAttachment(groupId, member, await photo());
		expect((await apiError(sendText(groupId, owner, null, null, [p.id]))).code).toBe('validation_error');
		const other = (await createGroup(member, { name: 'Elsewhere', requirement: everyDay() }, 'UTC')).id;
		expect((await apiError(sendText(other, member, null, null, [p.id]))).code).toBe('validation_error');
		await sendText(groupId, member, null, null, [p.id]);
		expect((await apiError(sendText(groupId, member, null, null, [p.id]))).code).toBe('validation_error');
		expect((await apiError(sendText(groupId, member, null, null, []))).code).toBe('validation_error');
		await deleteGroup(other, member);
	});

	test('captions can be edited or removed; an older app resaving the summary changes nothing', async () => {
		const p = await uploadAttachment(groupId, member, await photo());
		const sent = await sendText(groupId, member, 'First', null, [p.id]);
		expect((await editMessage(sent.id, member, '')).caption).toBeNull();
		const resaved = await editMessage(sent.id, member, '📷 Photo');
		expect(resaved).toMatchObject({ caption: null, body: '📷 Photo' });
		expect((await apiError(editMessage((await sendText(groupId, member, 'plain')).id, member, ''))).code).toBe('validation_error');
	});
});

describe('access', () => {
	test('signed links work for anyone holding them; otherwise members only', async () => {
		const p = await uploadAttachment(groupId, member, await photo());
		const sent = await sendText(groupId, member, null, null, [p.id]);
		const [att] = sent.attachments;

		const res = await fetchAs(att.url, null);
		expect(res.status).toBe(200);
		expect(res.headers.get('content-type')).toBe('image/jpeg');
		expect(res.headers.get('content-disposition')).toStartWith('inline');
		expect((await fetchAs(att.thumbnailUrl!, null)).status).toBe(200);

		const unsigned = att.url.split('?')[0];
		expect((await apiError(fetchAs(unsigned, null))).code).toBe('not_found');
		expect((await apiError(fetchAs(unsigned, outsider))).code).toBe('not_found');
		expect((await fetchAs(unsigned, owner)).status).toBe(200);
		expect((await apiError(fetchAs(`${unsigned}?e=9999999999&s=forged`, null))).code).toBe('not_found');

		const f = await uploadAttachment(groupId, member, pdf());
		const file = (await sendText(groupId, member, null, null, [f.id])).attachments[0];
		const download = await fetchAs(file.url, null);
		expect(download.headers.get('content-disposition')).toBe(`attachment; filename*=UTF-8''Plan.pdf`);
		expect(await download.text()).toBe('%PDF-1.4 tiny');
	});

	test("an unsent upload is only its uploader's", async () => {
		const p = await uploadAttachment(groupId, member, pdf());
		const unsigned = p.url.split('?')[0];
		expect((await fetchAs(unsigned, member)).status).toBe(200);
		expect((await apiError(fetchAs(unsigned, owner))).code).toBe('not_found');
	});
});

describe('Wallet passes', () => {
	const logo = () => sharp({ create: { width: 320, height: 100, channels: 4, background: '#ffffff00' } }).png().toBuffer();

	test('a .pkpass becomes a pass card, kept byte for byte, served for Wallet', async () => {
		const bytes = makePass(undefined, new Uint8Array(await logo()));
		const p = await uploadAttachment(groupId, member, new File([bytes], 'Winter Gig.pkpass', { type: 'application/vnd.apple.pkpass' }));
		expect(p).toMatchObject({ kind: 'pass', name: 'Winter Gig.pkpass', mime: 'application/vnd.apple.pkpass', size: bytes.byteLength });
		expect(p.pass).toMatchObject({ description: 'Concert ticket', organizationName: 'Roundhouse', serialNumber: 'SEAT-12A', backgroundColor: '#201840' });
		// The logo, shrunk, as its thumbnail.
		expect(p.thumbnailUrl).toContain('/thumb?');

		const sent = await sendText(groupId, member, null, null, [p.id]);
		expect(sent).toMatchObject({ body: '🎟️ Concert ticket', caption: null });
		const res = await fetchAs(sent.attachments[0].url, null);
		expect(res.headers.get('content-type')).toBe('application/vnd.apple.pkpass');
		expect(res.headers.get('content-disposition')).toStartWith('inline');
		expect(res.headers.get('content-security-policy')).not.toContain('sandbox');
		expect(new Uint8Array(await res.arrayBuffer())).toEqual(bytes);
		const thumb = await fetchAs(sent.attachments[0].thumbnailUrl!, null);
		expect(thumb.headers.get('content-type')).toBe('image/png');
		expect((await listMessages(groupId, owner, null, 50)).messages.find((m) => m.id === sent.id)?.attachments[0].pass?.eventName).toBe('Winter Gig');
	});

	test("a pass shared without its extension still counts; one that can't be read is a plain file", async () => {
		const shared = await uploadAttachment(groupId, member, new File([makePass()], 'ticket', { type: 'application/vnd.apple.pkpass' }));
		expect(shared).toMatchObject({ kind: 'pass', name: 'ticket.pkpass', thumbnailUrl: null });
		const broken = await uploadAttachment(groupId, member, new File(['not a zip'], 'broken.pkpass'));
		expect(broken).toMatchObject({ kind: 'file', mime: 'application/vnd.apple.pkpass', pass: null });
	});
});

describe('clean-up', () => {
	test('deleting a message deletes its files', async () => {
		const p = await uploadAttachment(groupId, member, await photo());
		const sent = await sendText(groupId, member, 'Oops', null, [p.id]);
		const { storageKey, thumbKey } = await keysOf(p.id);
		await deleteMessage(sent.id, member);
		expect(await db.query.messageAttachments.findFirst({ where: eq(messageAttachments.id, p.id) })).toBeUndefined();
		expect(await getObject(storageKey)).toBeNull();
		expect(await getObject(thumbKey!)).toBeNull();
		const after = (await listMessages(groupId, owner, null, 50)).messages.find((m) => m.id === sent.id)!;
		expect(after).toMatchObject({ deleted: true, body: null, attachments: [], caption: null });
	});

	test('uploads never sent are deleted after a day', async () => {
		const p = await uploadAttachment(groupId, member, pdf());
		const { storageKey } = await keysOf(p.id);
		await deleteAbandonedUploads(Date.now());
		expect(await getObject(storageKey)).not.toBeNull();
		await deleteAbandonedUploads(Date.now() + PENDING_TTL_MS + 60_000);
		expect(await getObject(storageKey)).toBeNull();
	});

	test("deleting the group deletes everything sent in it", async () => {
		const g = (await createGroup(owner, { name: 'Short-lived', requirement: everyDay() }, 'UTC')).id;
		const p = await uploadAttachment(g, owner, await photo());
		await sendText(g, owner, null, null, [p.id]);
		const { storageKey, thumbKey } = await keysOf(p.id);
		await deleteGroup(g, owner);
		expect(await getObject(storageKey)).toBeNull();
		expect(await getObject(thumbKey!)).toBeNull();
	});
});
