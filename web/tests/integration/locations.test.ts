import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import { createGroup } from '#lib/server/groups';
import { deleteMessage, editMessage, listMessages, sendLocation, sendText } from '#lib/server/chat';
import { listNotifications } from '#lib/server/notifications';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('locations');
afterAll(() => fx.cleanup());

let owner: string, member: string, outsider: string, groupId: string;

beforeAll(async () => {
	owner = await fx.user({ name: 'Ola' });
	member = await fx.user({ name: 'Mo' });
	outsider = await fx.user();
	groupId = (await createGroup(owner, { name: 'Night out', requirement: everyDay() }, 'UTC')).id;
	await fx.join(groupId, member);
});

const dishoom = { lat: 51.524_545_678, lng: -0.076_855, name: 'Dishoom Shoreditch', address: '7 Boundary St, London E2 7JE', accuracy: null };

describe('locations in chat', () => {
	test('a place comes back with its location, and a text summary with a maps link for older apps', async () => {
		const sent = await sendLocation(groupId, member, dishoom);
		expect(sent).toMatchObject({ kind: 'text', location: dishoom, attachments: [], caption: null });
		expect(sent.body).toBe(
			'📍 Dishoom Shoreditch\n7 Boundary St, London E2 7JE\nhttps://www.google.com/maps/search/?api=1&query=51.524546,-0.076855'
		);

		const listed = (await listMessages(groupId, owner, null, 50)).messages.find((m) => m.id === sent.id);
		expect(listed?.location).toEqual(dishoom);

		const { notifications } = await listNotifications(owner);
		expect(notifications.find((n) => n.kind === 'message' && n.groupId === groupId)?.body).toBe('Mo: 📍 Dishoom Shoreditch');
	});

	test('my location: no name, quoted as "📍 Location"; replies work both ways', async () => {
		const here = await sendLocation(groupId, owner, { lat: -33.8568, lng: 151.2153, name: null, address: null, accuracy: 15 });
		expect(here.body).toBe('📍 Location\nhttps://www.google.com/maps/search/?api=1&query=-33.8568,151.2153');
		const reply = await sendText(groupId, member, 'On my way', here.id);
		expect(reply.replyTo).toMatchObject({ id: here.id, text: '📍 Location' });
		const answer = await sendLocation(groupId, member, dishoom, reply.id);
		expect(answer.replyTo?.text).toBe('On my way');
		expect(reply.location).toBeNull();
	});

	test("can't be edited; deleting clears it; only members can send", async () => {
		const sent = await sendLocation(groupId, member, dishoom);
		expect((await apiError(editMessage(sent.id, member, 'Somewhere else'))).code).toBe('validation_error');
		await deleteMessage(sent.id, member);
		const listed = (await listMessages(groupId, owner, null, 50)).messages.find((m) => m.id === sent.id);
		expect(listed).toMatchObject({ deleted: true, body: null, location: null });
		expect((await apiError(sendLocation(groupId, outsider, dishoom))).code).toBe('not_found');
	});
});
