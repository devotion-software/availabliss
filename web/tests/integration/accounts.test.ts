import './setup';
import { afterAll, describe, expect, spyOn, test } from 'bun:test';
import { APIError } from 'better-auth';
import { auth } from '#lib/auth';
import { env } from '#lib/server/env';
import { and, eq } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { account, groupMembers, groups, requirementTemplates, session, user, userAliases, userPlans } from '#lib/server/db/schema';
import { createGroup } from '#lib/server/groups';
import { createTemplate } from '#lib/server/templates';
import { completeMerge, createMergeIntent, linkWithIdToken, listLinkedAccounts, mergeCandidate, mergeUsers } from '#lib/server/accounts';
import { newId } from '#lib/server/ids';
import { everyDay, fixture } from './fixtures';

const fx = fixture('accounts');
afterAll(() => fx.cleanup());

async function addAccount(userId: string, providerId: string) {
	await db.insert(account).values({ id: newId(), userId, providerId, accountId: `sub-${newId()}`, createdAt: new Date(), updatedAt: new Date() });
}

async function addSession(userId: string, createdAt = new Date()) {
	const id = newId();
	await db.insert(session).values({ id, userId, token: `tok-${id}`, expiresAt: new Date(Date.now() + 86_400_000), createdAt, updatedAt: createdAt });
	return id;
}

const roleIn = async (groupId: string, userId: string) =>
	(await db.query.groupMembers.findFirst({ where: and(eq(groupMembers.groupId, groupId), eq(groupMembers.userId, userId)) }))?.role;

describe('mergeUsers', () => {
	test('moves groups, templates, sessions and sign-ins, then deletes the other account', async () => {
		const into = await fx.user();
		const from = await fx.user({ image: 'https://example.test/me.png', country: 'GB' });
		const onlyFrom = await createGroup(from, { name: 'Only theirs', requirement: everyDay() }, 'UTC');
		const owner = await fx.user();
		const shared = await createGroup(owner, { name: 'Both in', requirement: everyDay() }, 'UTC');
		await fx.join(shared.id, into, 'member');
		await fx.join(shared.id, from, 'admin');
		await createTemplate(from, { name: 'Weekends', requirement: everyDay() });
		await addAccount(from, 'google');
		const sessionId = await addSession(from);

		expect(await mergeCandidate(from)).toMatchObject({ groups: 2 });
		await mergeUsers(from, into);

		expect(await db.query.user.findFirst({ where: eq(user.id, from) })).toBeUndefined();
		expect(await roleIn(onlyFrom.id, into)).toBe('owner');
		expect((await db.query.groups.findFirst({ where: eq(groups.id, onlyFrom.id) }))?.createdBy).toBe(into);
		// Both were in it: one membership, with the stronger role.
		expect(await roleIn(shared.id, into)).toBe('admin');
		expect(await db.select().from(groupMembers).where(eq(groupMembers.groupId, shared.id))).toHaveLength(2);
		expect(await db.select().from(requirementTemplates).where(eq(requirementTemplates.userId, into))).toHaveLength(1);
		expect((await db.query.session.findFirst({ where: eq(session.id, sessionId) }))?.userId).toBe(into);
		expect((await listLinkedAccounts(into)).linked.map((a) => a.provider)).toEqual(['google']);
		// Filled in what the remaining account lacked.
		expect(await db.query.user.findFirst({ where: eq(user.id, into) })).toMatchObject({ image: 'https://example.test/me.png', country: 'GB' });
		// Remembered for RevenueCat events that still use the old id.
		expect((await db.query.userAliases.findFirst({ where: eq(userAliases.oldUserId, from) }))?.userId).toBe(into);
	});

	test('keeps the better Pro plan', async () => {
		const into = await fx.user();
		const from = await fx.user();
		const later = new Date(Date.now() + 30 * 86_400_000);
		await db.insert(userPlans).values({ userId: into, proUntil: new Date(Date.now() + 86_400_000), store: 'app_store' });
		await db.insert(userPlans).values({ userId: from, proUntil: later, store: 'play_store' });
		await mergeUsers(from, into);
		const plan = await db.query.userPlans.findFirst({ where: eq(userPlans.userId, into) });
		expect(plan?.store).toBe('play_store');
		expect(plan?.proUntil?.getTime()).toBe(later.getTime());
	});
});

describe('web merge (completeMerge)', () => {
	test('merges the account signed in through the provider after the intent', async () => {
		const into = await fx.user();
		const from = await fx.user();
		await addAccount(from, 'apple');
		const code = await createMergeIntent(into, 'apple');
		const result = await completeMerge(code, { userId: from, sessionCreatedAt: new Date() });
		expect(result).toEqual({ ok: true, provider: 'apple', merged: true });
		expect(await db.query.user.findFirst({ where: eq(user.id, from) })).toBeUndefined();
	});

	test('codes are single use and need a sign-in made after the intent', async () => {
		const into = await fx.user();
		const from = await fx.user();
		await addAccount(from, 'apple');
		const code = await createMergeIntent(into, 'apple');
		expect(await completeMerge(code, { userId: from, sessionCreatedAt: new Date(Date.now() - 60_000) })).toEqual({ ok: false, reason: 'merge_not_signed_in' });
		expect(await completeMerge(code, { userId: from, sessionCreatedAt: new Date() })).toEqual({ ok: false, reason: 'merge_expired' });
		expect(await completeMerge(undefined, { userId: from, sessionCreatedAt: new Date() })).toEqual({ ok: false, reason: 'merge_expired' });
		expect(await db.query.user.findFirst({ where: eq(user.id, from) })).toBeDefined();
	});

	test("won't merge an account that didn't sign in with that provider", async () => {
		const into = await fx.user();
		const from = await fx.user();
		await addAccount(from, 'google');
		const code = await createMergeIntent(into, 'apple');
		expect(await completeMerge(code, { userId: from, sessionCreatedAt: new Date() })).toEqual({ ok: false, reason: 'merge_wrong_account' });
	});
});

describe('linkWithIdToken', () => {
	test('an id token Better Auth rejects is a 400, not a 401 that would sign the app out', async () => {
		const me = await fx.user();
		const google = env.google as { clientIds: string[]; clientSecret: string | null };
		const saved = { clientIds: google.clientIds, clientSecret: google.clientSecret };
		google.clientIds = saved.clientIds.length ? saved.clientIds : ['test-client'];
		google.clientSecret = saved.clientSecret ?? 'test-secret';
		const link = spyOn(auth.api, 'linkSocialAccount').mockImplementation((async () => {
			throw new APIError('UNAUTHORIZED', { message: 'Invalid id token' });
		}) as never);
		try {
			await expect(linkWithIdToken(me, new Headers(), { provider: 'google', idToken: 'expired' })).rejects.toMatchObject({ status: 400, code: 'validation_error' });
		} finally {
			link.mockRestore();
			Object.assign(google, saved);
		}
	});
});

