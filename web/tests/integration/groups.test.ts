import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import { eq } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { groupInvites, groups } from '#lib/server/db/schema';
import {
	acceptInvite,
	createGroup,
	createInvite,
	deleteGroup,
	getGroup,
	groupIdsFor,
	groupsFor,
	memberIds,
	previewInvite,
	removeMember,
	requireMember,
	roleIn,
	setMemberRole,
	updateGroup,
	usersByIds
} from '#lib/server/groups';
import { FREE_GROUP_LIMIT, getPlan, saveEntitlement } from '#lib/server/billing';
import { createBlock } from '#lib/server/blocks';
import { nudgeUnsynced } from '#lib/server/nudge';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('groups');
afterAll(() => fx.cleanup());

describe('createGroup', () => {
	test('creates the group with the creator as owner and a default weekend timing', async () => {
		const owner = await fx.user({ name: 'Ada Lovelace' });
		const group = await createGroup(owner, { name: 'Lake District crew', color: 't3' }, 'Europe/London');
		expect(group).toMatchObject({ name: 'Lake District crew', color: 't3', monogram: 'LD', role: 'owner', memberCount: 1, syncedCount: 0 });
		expect(group.members).toEqual([expect.objectContaining({ userId: owner, role: 'owner', name: 'Ada Lovelace', synced: false })]);
		expect(group.requirement.length).toMatchObject({ type: 'span', startDay: 4, endDay: 6 });
		expect(group.requirement.timezone).toBe('Europe/London');
		expect(group.requirementSummary).toBeTruthy();
		const row = await db.query.groups.findFirst({ where: eq(groups.id, group.id) });
		expect(row?.createdBy).toBe(owner);
	});

	test('keeps a given requirement and picks a valid random colour', async () => {
		const owner = await fx.user();
		const group = await createGroup(owner, { name: 'Book club', requirement: everyDay() }, 'UTC');
		expect(group.requirement).toEqual(everyDay());
		expect(['t1', 't2', 't3', 't4', 't5']).toContain(group.color);
	});

	test(`free accounts can own ${FREE_GROUP_LIMIT} groups; Pro lifts the limit`, async () => {
		const owner = await fx.user();
		for (let i = 0; i < FREE_GROUP_LIMIT; i++) await createGroup(owner, { name: `Free ${i}` }, 'UTC');

		const plan = await getPlan(owner);
		expect(plan).toMatchObject({ tier: 'free', groupLimit: FREE_GROUP_LIMIT, groupsOwned: FREE_GROUP_LIMIT, canCreateGroup: false });

		const err = await apiError(createGroup(owner, { name: 'One too many' }, 'UTC'));
		expect(err.code).toBe('upgrade_required');
		expect(err.status).toBe(402);
		expect((await groupsFor(owner)).length).toBe(FREE_GROUP_LIMIT);

		await saveEntitlement(owner, { proUntil: new Date(Date.now() + 86_400_000), lifetime: false, store: 'app_store', productId: 'pro_monthly', willRenew: true });
		const fourth = await createGroup(owner, { name: 'Pro group' }, 'UTC');
		expect(fourth.role).toBe('owner');
		expect(await getPlan(owner)).toMatchObject({ tier: 'pro', groupLimit: null, groupsOwned: FREE_GROUP_LIMIT + 1, canCreateGroup: true });
	});

	test('groups you only belong to do not count towards the limit', async () => {
		const [owner, other] = await fx.users(2);
		for (let i = 0; i < FREE_GROUP_LIMIT; i++) {
			const g = await createGroup(other, { name: `Theirs ${i}` }, 'UTC');
			await fx.join(g.id, owner);
		}
		expect((await getPlan(owner)).groupsOwned).toBe(0);
		await createGroup(owner, { name: 'Mine' }, 'UTC');
	});
});

describe('membership and roles', () => {
	let owner: string, admin: string, member: string, outsider: string, groupId: string;

	beforeAll(async () => {
		[owner, admin, member, outsider] = await fx.users(4);
		groupId = (await createGroup(owner, { name: 'Roles', requirement: everyDay() }, 'UTC')).id;
		await fx.join(groupId, admin, 'admin');
		await fx.join(groupId, member);
	});

	test('roleIn / requireMember', async () => {
		expect(await roleIn(groupId, owner)).toBe('owner');
		expect(await roleIn(groupId, admin)).toBe('admin');
		expect(await roleIn(groupId, outsider)).toBeNull();

		expect(await requireMember(groupId, member)).toBe('member');
		expect(await requireMember(groupId, admin, 'admin')).toBe('admin');
		expect(await requireMember(groupId, owner, 'admin')).toBe('owner');
		expect((await apiError(requireMember(groupId, outsider))).code).toBe('not_found');
		expect((await apiError(requireMember(groupId, member, 'admin'))).code).toBe('forbidden');
		expect((await apiError(requireMember(groupId, admin, 'owner'))).code).toBe('forbidden');
	});

	test('outsiders cannot see the group (404, not 403)', async () => {
		expect((await apiError(getGroup(groupId, outsider))).status).toBe(404);
		expect((await apiError(getGroup('no-such-group', owner))).status).toBe(404);
	});

	test('getGroup lists members in join order and counts synced ones', async () => {
		await createBlock(member, { start: '2031-03-04T09:00:00Z', end: '2031-03-04T10:00:00Z', allDay: false, repeat: 'none', until: null, note: null });
		const group = await getGroup(groupId, member);
		expect(group.role).toBe('member');
		expect(group.members.map((m) => m.userId)).toEqual([owner, admin, member]);
		expect(group.syncedCount).toBe(1);
		expect(group.members.find((m) => m.userId === member)?.synced).toBe(true);
	});

	test('memberIds / groupIdsFor / usersByIds', async () => {
		expect((await memberIds(groupId)).sort()).toEqual([owner, admin, member].sort());
		expect(await groupIdsFor(member)).toEqual([groupId]);
		expect(await usersByIds([])).toEqual([]);
		const users = await usersByIds([owner, member]);
		expect(users.map((u) => u.id).sort()).toEqual([owner, member].sort());
	});

	test('only admins can update the group', async () => {
		expect((await apiError(updateGroup(groupId, member, { name: 'Nope' }))).code).toBe('forbidden');
		const updated = await updateGroup(groupId, admin, { name: 'Renamed group', color: 't5' });
		expect(updated).toMatchObject({ name: 'Renamed group', color: 't5', monogram: 'RG', role: 'admin' });
		// An empty patch is a no-op read.
		expect((await updateGroup(groupId, admin, {})).name).toBe('Renamed group');
	});

	test('setMemberRole: admins promote and demote; the owner is fixed', async () => {
		expect((await apiError(setMemberRole(groupId, member, admin, 'member'))).code).toBe('forbidden');
		expect((await apiError(setMemberRole(groupId, admin, owner, 'member'))).code).toBe('forbidden');
		expect((await apiError(setMemberRole(groupId, admin, outsider, 'admin'))).code).toBe('not_found');
		const promoted = await setMemberRole(groupId, admin, member, 'admin');
		expect(promoted).toMatchObject({ userId: member, role: 'admin' });
		await setMemberRole(groupId, owner, member, 'member');
		expect(await roleIn(groupId, member)).toBe('member');
	});

	test('removeMember: members can leave, only admins remove others, nobody removes the owner', async () => {
		const [leaver, kicked] = await fx.users(2);
		await fx.join(groupId, leaver);
		await fx.join(groupId, kicked);

		expect((await apiError(removeMember(groupId, member, kicked))).code).toBe('forbidden');
		expect((await apiError(removeMember(groupId, admin, owner))).code).toBe('forbidden');
		expect((await apiError(removeMember(groupId, owner, owner))).code).toBe('forbidden');
		expect((await apiError(removeMember(groupId, admin, outsider))).code).toBe('not_found');
		expect((await apiError(removeMember(groupId, outsider, member))).code).toBe('not_found');

		await removeMember(groupId, leaver, leaver);
		await removeMember(groupId, admin, kicked);
		expect(await roleIn(groupId, leaver)).toBeNull();
		expect(await roleIn(groupId, kicked)).toBeNull();
	});

	test('only the owner can delete the group', async () => {
		const g = await createGroup(owner, { name: 'Short lived' }, 'UTC');
		await fx.join(g.id, admin, 'admin');
		expect((await apiError(deleteGroup(g.id, admin))).code).toBe('forbidden');
		expect((await deleteGroup(g.id, owner)).sort()).toEqual([owner, admin].sort());
		expect(await db.query.groups.findFirst({ where: eq(groups.id, g.id) })).toBeUndefined();
		expect(await roleIn(g.id, admin)).toBeNull();
	});
});

describe('invites', () => {
	let owner: string, member: string, joiner: string, groupId: string;

	beforeAll(async () => {
		owner = await fx.user({ name: 'Owner Person' });
		[member, joiner] = await fx.users(2);
		groupId = (await createGroup(owner, { name: 'Invites' }, 'UTC')).id;
		await fx.join(groupId, member);
	});

	test('admins create invites that expire in 14 days; members cannot', async () => {
		expect((await apiError(createInvite(groupId, member, null))).code).toBe('forbidden');
		const { token, expiresAt } = await createInvite(groupId, owner, 'friend@example.com');
		expect(token).toMatch(/^[0-9A-Za-z]{32}$/);
		const days = (expiresAt.getTime() - Date.now()) / 86_400_000;
		expect(days).toBeGreaterThan(13.9);
		expect(days).toBeLessThanOrEqual(14);
	});

	test('preview, accept (idempotent) and expiry', async () => {
		const { token } = await createInvite(groupId, owner, null);
		expect(await previewInvite(token)).toMatchObject({ groupName: 'Invites', inviterName: 'Owner Person', memberCount: 2 });

		expect(await acceptInvite(token, joiner)).toEqual({ groupId, joined: true });
		expect(await acceptInvite(token, joiner)).toEqual({ groupId, joined: false });
		expect(await roleIn(groupId, joiner)).toBe('member');
		// Accepting as an existing admin/owner doesn't demote them.
		expect(await acceptInvite(token, owner)).toEqual({ groupId, joined: false });
		expect(await roleIn(groupId, owner)).toBe('owner');

		// Two taps at once: one joins, the other is a no-op instead of a 500.
		const racer = await fx.user();
		const both = await Promise.all([acceptInvite(token, racer), acceptInvite(token, racer)]);
		expect(both.filter((r) => r.joined)).toHaveLength(1);
		expect(await roleIn(groupId, racer)).toBe('member');

		await db.update(groupInvites).set({ expiresAt: new Date(Date.now() - 1000) }).where(eq(groupInvites.token, token));
		expect((await apiError(previewInvite(token))).code).toBe('not_found');
		expect((await apiError(acceptInvite(token, joiner))).code).toBe('not_found');
		expect((await apiError(previewInvite('not-a-real-token'))).code).toBe('not_found');
	});
});

describe('nudge', () => {
    test('two admins nudging at once: one gets through, the other is rate limited', async () => {
        const [owner, admin, quiet] = await fx.users(3);
        const g = await createGroup(owner, { name: 'Nudgers', requirement: everyDay() }, 'UTC');
        await fx.join(g.id, admin, 'admin');
        await fx.join(g.id, quiet);
        const results = await Promise.allSettled([nudgeUnsynced(g.id, owner), nudgeUnsynced(g.id, admin)]);
        expect(results.filter((r) => r.status === 'fulfilled')).toHaveLength(1);
        const rejected = results.find((r) => r.status === 'rejected') as PromiseRejectedResult;
        expect(rejected.reason.code).toBe('rate_limited');
    });
});
