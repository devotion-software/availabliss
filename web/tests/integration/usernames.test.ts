import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { eq } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { user } from '#lib/server/db/schema';
import { acceptInvite, createGroup, createInvite, listInvites, memberIds, previewInvite } from '#lib/server/groups';
import { getMe, updateMe } from '#lib/server/users';
import { assignUsername, checkUsername, findByUsername } from '#lib/server/usernames';
import { apiError, fixture } from './fixtures';

const fx = fixture('usernames');
afterAll(() => fx.cleanup());

// Unique per run, so reruns against the dev database don't collide.
const tag = Math.random().toString(36).slice(2, 8);

describe('assignUsername', () => {
	test("makes one from the name, then adds a number when it's taken", async () => {
		const name = `Ada ${tag}`;
		const [a, b] = [await fx.user({ name }), await fx.user({ name })];
		expect(await assignUsername(a)).toBe(`ada${tag}`);
		expect(await assignUsername(b)).toBe(`ada${tag}2`);
		// Keeps the one it has.
		expect(await assignUsername(a)).toBe(`ada${tag}`);
	});

	test('uses the email when there is no name, and getMe fills in a missing one', async () => {
		const id = await fx.user({ name: '', email: `zed.${tag}@test.availabliss.invalid` });
		const me = await getMe(id);
		expect(me.username).toBe(`zed${tag}`);
	});
});

describe('updateMe username', () => {
	test("saves it lowercase and refuses one that's taken", async () => {
		const [a, b] = await fx.users(2);
		const me = await updateMe(a, { username: `pick_${tag}` });
		expect(me.username).toBe(`pick_${tag}`);
		const err = await apiError(updateMe(b, { username: `pick_${tag}` }));
		expect(err.code).toBe('conflict');
		// Setting your own again is fine.
		expect((await updateMe(a, { username: `pick_${tag}` })).username).toBe(`pick_${tag}`);
	});
});

describe('checkUsername', () => {
	test('reports problems, who has it, and whether you can take it', async () => {
		const [a, b] = await fx.users(2);
		await updateMe(a, { username: `check_${tag}` });

		expect(await checkUsername(`@Check_${tag}`, a)).toMatchObject({ username: `check_${tag}`, available: true, user: { id: a } });
		expect(await checkUsername(`check_${tag}`, b)).toMatchObject({ available: false, problem: null, user: { id: a } });
		expect(await checkUsername(`free_${tag}`, b)).toMatchObject({ available: true, user: null });
		expect(await checkUsername('a.', b)).toMatchObject({ available: false, problem: expect.any(String), user: null });
		expect(await findByUsername(`@CHECK_${tag}`)).toMatchObject({ id: a });
	});
});

describe('invites by username', () => {
	test('only the invited person can join, and the invite is used up', async () => {
		const [owner, invitee, other] = await fx.users(3);
		await db
			.update(user)
			.set({ username: `inv_${tag}` })
			.where(eq(user.id, invitee));
		const group = await createGroup(owner, { name: 'Username invites' }, 'UTC');
		const { token } = await createInvite(group.id, owner, null, invitee);

		expect(await listInvites(group.id, owner)).toEqual([expect.objectContaining({ email: null, username: `inv_${tag}` })]);
		expect(await previewInvite(token)).toMatchObject({ email: null, username: `inv_${tag}` });

		const err = await apiError(acceptInvite(token, other));
		expect(err.code).toBe('forbidden');
		expect(err.message).toContain(`@inv_${tag}`);

		expect(await acceptInvite(token, invitee)).toEqual({ groupId: group.id, joined: true });
		expect(await memberIds(group.id)).toContain(invitee);
		expect(await listInvites(group.id, owner)).toEqual([]);
	});
});
