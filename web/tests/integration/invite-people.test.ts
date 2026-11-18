import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { and, eq } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { notifications } from '#lib/server/db/schema';
import { acceptInvite, createGroup, createInvite, declineInvite, listInvites, memberIds, previewInvite } from '#lib/server/groups';
import { POST as invite } from '../../src/routes/api/v1/groups/[id]/invites/+server';
import { apiError, fixture } from './fixtures';

const fx = fixture('invite-people');
afterAll(() => fx.cleanup());

/** Calls `POST /groups/:id/invites` as `userId`. */
async function invitePeople(groupId: string, userId: string, body: unknown) {
	const request = new Request(`http://localhost/api/v1/groups/${groupId}/invites`, {
		method: 'POST',
		headers: { 'content-type': 'application/json' },
		body: JSON.stringify(body)
	});
	const res = await (invite as (e: unknown) => Promise<Response>)({ request, params: { id: groupId }, url: new URL(request.url), locals: { user: { id: userId } } });
	return { status: res.status, body: await res.json() };
}

const inviteItems = (userId: string) =>
	db.select().from(notifications).where(and(eq(notifications.userId, userId), eq(notifications.kind, 'invite.received')));

describe('a new group with the same people', () => {
	test("invites people you share a group with by id; each gets an Activity invite only they can accept", async () => {
		const [sam, ana, ben, stranger] = await fx.users(4);
		const old = await createGroup(sam, { name: 'Book club' }, 'UTC');
		for (const id of [ana, ben]) await acceptInvite((await createInvite(old.id, sam, null)).token, id);
		const next = await createGroup(sam, { name: 'Book club 2' }, 'UTC');

		const res = await invitePeople(next.id, sam, { userIds: [ana, ben, ana] });
		expect(res.status).toBe(201);
		expect(res.body.invites).toHaveLength(2);
		expect(res.body.invites[0]).toMatchObject({ email: null, name: expect.any(String) });
		expect(await listInvites(next.id, sam)).toHaveLength(2);

		const [item] = await inviteItems(ana);
		expect(item).toMatchObject({ title: 'Book club 2', groupId: null });
		const token = item.data.token;

		// Only Ana can use hers.
		expect((await apiError(acceptInvite(token, stranger))).code).toBe('forbidden');
		expect(await acceptInvite(token, ana)).toEqual({ groupId: next.id, joined: true });
		expect(await memberIds(next.id)).toContain(ana);
		expect(await listInvites(next.id, sam)).toHaveLength(1);
	});

	test("can't reach strangers or people already in the group", async () => {
		const [sam, ana, stranger] = await fx.users(3);
		const old = await createGroup(sam, { name: 'Walks' }, 'UTC');
		await acceptInvite((await createInvite(old.id, sam, null)).token, ana);
		const next = await createGroup(sam, { name: 'Walks 2' }, 'UTC');

		const strangers = await invitePeople(next.id, sam, { userIds: [ana, stranger] });
		expect(strangers.status).toBe(404);
		// Nothing was sent to anyone.
		expect(await listInvites(next.id, sam)).toEqual([]);
		expect(await inviteItems(ana)).toEqual([]);

		expect((await invitePeople(old.id, sam, { userIds: [ana] })).status).toBe(409);
		// A plain member can't invite.
		expect((await invitePeople(old.id, ana, { userIds: [sam] })).status).toBe(403);
	});
});

describe('declineInvite', () => {
	test('only the invitee can decline; it removes the invite and its Activity item', async () => {
		const [sam, ana, ben] = await fx.users(3);
		const old = await createGroup(sam, { name: 'Climbing' }, 'UTC');
		await acceptInvite((await createInvite(old.id, sam, null)).token, ana);
		const next = await createGroup(sam, { name: 'Climbing 2' }, 'UTC');
		await invitePeople(next.id, sam, { userIds: [ana] });
		const [item] = await inviteItems(ana);
		const token = item.data.token;
		expect(await previewInvite(token)).toMatchObject({ personal: true, groupName: 'Climbing 2' });

		expect((await apiError(declineInvite(token, ben))).code).toBe('forbidden');
		await declineInvite(token, ana);
		expect(await listInvites(next.id, sam)).toEqual([]);
		expect(await inviteItems(ana)).toEqual([]);
		expect((await apiError(previewInvite(token))).code).toBe('not_found');
		expect(await memberIds(next.id)).not.toContain(ana);
	});

	test("a link anyone can use can't be declined", async () => {
		const [sam, ana] = await fx.users(2);
		const group = await createGroup(sam, { name: 'Open link' }, 'UTC');
		const { token } = await createInvite(group.id, sam, null);
		expect(await previewInvite(token)).toMatchObject({ personal: false });
		expect((await apiError(declineInvite(token, ana))).code).toBe('forbidden');
		expect(await listInvites(group.id, sam)).toHaveLength(1);
	});
});
