import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { acceptInvite, createGroup, createInvite, listInvites, memberIds, previewInvite } from '#lib/server/groups';
import { POST as invite } from '../../src/routes/api/v1/groups/[id]/invites/+server';
import { apiError, fixture } from './fixtures';

const fx = fixture('invite-links');
afterAll(() => fx.cleanup());

/** Calls `POST /groups/:id/invites` as `userId`. */
async function createLink(groupId: string, userId: string, body: unknown) {
	const request = new Request(`http://localhost/api/v1/groups/${groupId}/invites`, {
		method: 'POST',
		headers: { 'content-type': 'application/json' },
		body: JSON.stringify(body)
	});
	const res = await (invite as (e: unknown) => Promise<Response>)({ request, params: { id: groupId }, url: new URL(request.url), locals: { user: { id: userId } } });
	return { status: res.status, body: await res.json() };
}

describe('how many people can join with a link', () => {
	test('left out it is one person (as before); null is no limit; 0 or a fraction is refused', async () => {
		const [owner] = await fx.users(1);
		const g = await createGroup(owner, { name: 'Limits' }, 'UTC');

		const one = await createLink(g.id, owner, {});
		expect(one.status).toBe(201);
		expect(one.body.invites[0]).toMatchObject({ maxUses: 1, uses: 0 });
		const five = await createLink(g.id, owner, { maxUses: 5 });
		expect(five.body.invites[0]).toMatchObject({ maxUses: 5, uses: 0 });
		const unlimited = await createLink(g.id, owner, { maxUses: null });
		expect(unlimited.body.invites[0]).toMatchObject({ maxUses: null, uses: 0 });
		expect((await createLink(g.id, owner, { maxUses: 0 })).status).toBe(400);
		expect((await createLink(g.id, owner, { maxUses: 2.5 })).status).toBe(400);

		// An emailed invite is for one person whatever's asked.
		const emailed = await createLink(g.id, owner, { emails: ['someone@test.availabliss.invalid'], maxUses: null });
		expect(emailed.body.invites[0]).toMatchObject({ maxUses: 1 });
	});

	test('counts each person who joins, then is used up: off the list, and the link says so', async () => {
		const [owner, a, b, c] = await fx.users(4);
		const g = await createGroup(owner, { name: 'Three of us' }, 'UTC');
		const { id, token } = await createInvite(g.id, owner, null, null, 2);

		expect(await acceptInvite(token, a)).toEqual({ groupId: g.id, joined: true });
		// Joining again (already in) doesn't use a place.
		expect(await acceptInvite(token, a)).toEqual({ groupId: g.id, joined: false });
		expect((await listInvites(g.id, owner)).find((i) => i.id === id)).toMatchObject({ maxUses: 2, uses: 1 });

		expect(await acceptInvite(token, b)).toEqual({ groupId: g.id, joined: true });
		expect((await listInvites(g.id, owner)).map((i) => i.id)).not.toContain(id);
		const preview = await apiError(previewInvite(token));
		expect(preview).toMatchObject({ code: 'not_found', message: 'This invite link has been used up' });
		const late = await apiError(acceptInvite(token, c));
		expect(late).toMatchObject({ code: 'not_found', message: 'This invite link has been used up' });
		expect(await memberIds(g.id)).not.toContain(c);
	});

	test('no limit: anyone can keep joining until it expires', async () => {
		const people = await fx.users(5);
		const [owner, ...others] = people;
		const g = await createGroup(owner, { name: 'Open house' }, 'UTC');
		const { id, token } = await createInvite(g.id, owner, null, null, null);
		for (const p of others) expect((await acceptInvite(token, p)).joined).toBe(true);
		expect((await listInvites(g.id, owner)).find((i) => i.id === id)).toMatchObject({ maxUses: null, uses: 4 });
		expect(await previewInvite(token)).toMatchObject({ groupName: 'Open house' });
	});

	test('people joining at once never go past the limit', async () => {
		const [owner, ...joiners] = await fx.users(9);
		const g = await createGroup(owner, { name: 'Rush' }, 'UTC');
		const { id, token } = await createInvite(g.id, owner, null, null, 3);

		const results = await Promise.allSettled(joiners.map((p) => acceptInvite(token, p)));
		const joined = results.filter((r) => r.status === 'fulfilled' && r.value.joined);
		expect(joined).toHaveLength(3);
		for (const r of results) if (r.status === 'rejected') expect(r.reason.message).toBe('This invite link has been used up');

		// Only the three who got in are members (the rest weren't left half-added).
		const members = await memberIds(g.id);
		expect(members).toHaveLength(4);
		expect(joiners.filter((p) => members.includes(p))).toHaveLength(3);
		expect((await listInvites(g.id, owner)).map((i) => i.id)).not.toContain(id);
	});
});
