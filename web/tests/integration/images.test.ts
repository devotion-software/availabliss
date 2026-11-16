import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import sharp from 'sharp';
import { createGroup, getGroup, setGroupImage } from '#lib/server/groups';
import { createEvent, deleteEvent, getEvent, setEventImage } from '#lib/server/events';
import { keyOf, localFile } from '#lib/server/media';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('images');
afterAll(() => fx.cleanup());

let owner: string, admin: string, member: string, groupId: string;

/** A real (if dull) photo: 2400×1200 so the resize shows. */
const photo = async () => {
	const png = await sharp({ create: { width: 2400, height: 1200, channels: 3, background: '#ff9e78' } }).png().toBuffer();
	return png.buffer.slice(png.byteOffset, png.byteOffset + png.byteLength) as ArrayBuffer;
};
const sizeOf = async (url: string | null) => {
	const bytes = await localFile(keyOf(url)!);
	expect(bytes).not.toBeNull();
	return sharp(bytes!).metadata();
};

beforeAll(async () => {
	owner = await fx.user({ name: 'Ola' });
	[admin, member] = await fx.users(2);
	groupId = (await createGroup(owner, { name: 'Photos', requirement: everyDay() }, 'UTC')).id;
	await fx.join(groupId, admin, 'admin');
	await fx.join(groupId, member);
});

describe('group photo', () => {
	test('admins set and remove it; members see it; old files are deleted', async () => {
		expect((await getGroup(groupId, member)).image).toBeNull();
		expect((await apiError(setGroupImage(groupId, member, await photo()))).code).toBe('forbidden');

		const first = (await setGroupImage(groupId, admin, await photo())).image;
		expect(first).toContain('/groups/');
		expect((await getGroup(groupId, member)).image).toBe(first);
		const meta = await sizeOf(first);
		expect(Math.max(meta.width!, meta.height!)).toBe(512);

		const second = (await setGroupImage(groupId, owner, await photo())).image;
		expect(second).not.toBe(first);
		expect(await localFile(keyOf(first)!)).toBeNull();

		expect((await setGroupImage(groupId, admin, null)).image).toBeNull();
		expect(await localFile(keyOf(second)!)).toBeNull();
	});

	test('not a photo is a friendly error', async () => {
		const junk = new TextEncoder().encode('not an image').buffer as ArrayBuffer;
		expect((await apiError(setGroupImage(groupId, admin, junk))).code).toBe('validation_error');
	});
});

describe('event cover', () => {
	test('the creator or an admin sets it; it goes when the event does', async () => {
		const start = new Date(Date.now() + 3 * 86_400_000).toISOString();
		const end = new Date(Date.now() + 3 * 86_400_000 + 7_200_000).toISOString();
		const e = await createEvent(groupId, member, { title: 'Picnic', start, end, allDay: false, location: null, note: null });
		expect(e.image).toBeNull();

		const cover = (await setEventImage(e.id, member, await photo())).image;
		expect(cover).toContain('/events/');
		expect((await getEvent(e.id, owner)).image).toBe(cover);
		expect((await sizeOf(cover)).width).toBe(1600);

		const other = await createEvent(groupId, admin, { title: 'Admin’s', start, end, allDay: false, location: null, note: null });
		expect((await apiError(setEventImage(other.id, member, await photo()))).code).toBe('forbidden');

		await deleteEvent(e.id, member);
		expect(await localFile(keyOf(cover)!)).toBeNull();
	});
});
