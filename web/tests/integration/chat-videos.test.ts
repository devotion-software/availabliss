import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import sharp from 'sharp';
import { db } from '#lib/server/db';
import { userPlans } from '#lib/server/db/schema';
import { createGroup, deleteGroup } from '#lib/server/groups';
import { sendText } from '#lib/server/chat';
import { listGroupMedia, uploadAttachment } from '#lib/server/attachments';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('chat-videos');
afterAll(async () => {
	await deleteGroup(groupId, owner);
	await fx.cleanup();
});

let owner: string, pro: string, groupId: string;

beforeAll(async () => {
	owner = await fx.user({ name: 'Ola' });
	pro = await fx.user({ name: 'Pat' });
	groupId = (await createGroup(owner, { name: 'Clips', requirement: everyDay() }, 'UTC')).id;
	await fx.join(groupId, pro);
	await db.insert(userPlans).values({ userId: pro, lifetime: true });
});

/** A minimal MP4 (ftyp, then moov → mvhd) lasting `seconds`. */
function mp4(seconds: number, name = 'clip.mp4'): File {
	const box = (type: string, body: Uint8Array) => {
		const out = new Uint8Array(8 + body.length);
		new DataView(out.buffer).setUint32(0, out.length);
		out.set(new TextEncoder().encode(type), 4);
		out.set(body, 8);
		return out;
	};
	const mvhd = new Uint8Array(20);
	new DataView(mvhd.buffer).setUint32(12, 600);
	new DataView(mvhd.buffer).setUint32(16, Math.round(seconds * 600));
	const ftyp = box('ftyp', new TextEncoder().encode('isom0000'));
	const moov = box('moov', box('mvhd', mvhd));
	return new File([ftyp, moov], name, { type: 'video/mp4' });
}
const poster = async () =>
	new File(
		[
			await sharp({ create: { width: 1280, height: 720, channels: 3, background: '#335' } })
				.jpeg()
				.toBuffer()
		],
		'poster.jpg',
		{
			type: 'image/jpeg'
		}
	);

describe('videos in chat', () => {
	test('an MP4 with a poster is a video: its length, the poster as preview, on the media page', async () => {
		const video = await uploadAttachment(groupId, owner, mp4(95), await poster());
		expect(video).toMatchObject({ kind: 'video', mime: 'video/mp4', duration: 95, width: 720, height: 405 });
		expect(video.thumbnailUrl).toBeTruthy();
		const message = await sendText(groupId, owner, '', null, [video.id]);
		expect(message.body).toBe('🎥 Video');
		expect((await listGroupMedia(groupId, owner, 'media', null, 60)).items.map((i) => i.attachment.id)).toContain(video.id);
	});

	test('without a poster it still sends, with no preview', async () => {
		const video = await uploadAttachment(groupId, owner, mp4(10, 'IMG_1.MOV'));
		expect(video).toMatchObject({ kind: 'video', mime: 'video/quicktime', name: 'IMG_1.mov', duration: 10, thumbnailUrl: null });
	});

	test('3 minutes free, 10 with Pro; one that can’t be read is refused', async () => {
		expect((await apiError(uploadAttachment(groupId, owner, mp4(4 * 60)))).message).toContain('3 minutes');
		expect((await uploadAttachment(groupId, pro, mp4(4 * 60))).duration).toBe(240);
		expect((await apiError(uploadAttachment(groupId, pro, mp4(11 * 60)))).message).toContain('10 minutes');
		const notVideo = new File(['not a video at all'], 'fake.mp4', { type: 'video/mp4' });
		expect((await apiError(uploadAttachment(groupId, owner, notVideo))).message).toContain("couldn't be read");
	});
});
