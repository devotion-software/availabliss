import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { eq } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { userPlans } from '#lib/server/db/schema';
import { getPlan, handleRevenueCatEvent, isPro, requireCanCreateGroup, saveEntitlement, syncFromRevenueCat, type RcEvent } from '#lib/server/billing';
import { createGroup } from '#lib/server/groups';
import { apiError, fixture } from './fixtures';

const fx = fixture('billing');
afterAll(() => fx.cleanup());

const DAY = 86_400_000;
const planRow = (userId: string) => db.query.userPlans.findFirst({ where: eq(userPlans.userId, userId) });
const purchase = (userId: string, overrides: Partial<RcEvent> = {}): RcEvent => ({
	type: 'INITIAL_PURCHASE',
	app_user_id: userId,
	entitlement_ids: ['pro'],
	expiration_at_ms: Date.now() + 30 * DAY,
	store: 'APP_STORE',
	product_id: 'pro_monthly',
	...overrides
});

describe('getPlan', () => {
	test('a new account is free with the purchase config exposed', async () => {
		const me = await fx.user();
		const plan = await getPlan(me);
		expect(plan).toMatchObject({ tier: 'free', proUntil: null, lifetime: false, willRenew: false, store: null, groupLimit: 3, groupsOwned: 0, canCreateGroup: true });
		// setup.ts blanks ANTHROPIC_API_KEY.
		expect(plan.aiPlanner).toEqual({ available: false, included: false });
		expect(plan.purchase.entitlement).toBeTruthy();
		expect(await isPro(me)).toBe(false);
		await requireCanCreateGroup(me);
	});

	test('active subscription and expired Pro', async () => {
		const [sub, lapsed] = await fx.users(2);
		const until = new Date(Math.floor((Date.now() + 10 * DAY) / 1000) * 1000);
		await saveEntitlement(sub, { proUntil: until, lifetime: false, store: 'play_store', productId: 'pro_annual', willRenew: false });
		expect(await getPlan(sub)).toMatchObject({ tier: 'pro', proUntil: until.toISOString(), lifetime: false, willRenew: false, store: 'play_store', groupLimit: null, canCreateGroup: true, aiPlanner: { available: false, included: true } });

		await saveEntitlement(lapsed, { proUntil: new Date(Date.now() - DAY), lifetime: false, store: 'app_store', productId: 'pro_monthly', willRenew: true });
		expect(await getPlan(lapsed)).toMatchObject({ tier: 'free', store: null, willRenew: false, groupLimit: 3 });
		expect(await isPro(lapsed)).toBe(false);
	});

	/*
	 * BUG: lifetime Pro is stored as 2999-01-01, but `pro_until` is a TIMESTAMP column,
	 * which MariaDB caps at 2038 (2106 on 11.5+), so the insert fails with
	 * ER_TRUNCATED_WRONG_VALUE. Marked `failing` until that's fixed; flip it to `test` then.
	 */
	test('lifetime Pro', async () => {
		const life = await fx.user();
		await saveEntitlement(life, { proUntil: null, lifetime: true, store: 'stripe', productId: 'pro_lifetime', willRenew: false });
		expect(await getPlan(life)).toMatchObject({ tier: 'pro', proUntil: null, lifetime: true });
	});

	test('saveEntitlement upserts', async () => {
		const me = await fx.user();
		await saveEntitlement(me, { proUntil: new Date(Date.now() + DAY), lifetime: false, store: 'app_store', productId: 'a', willRenew: true });
		await saveEntitlement(me, { proUntil: new Date(Date.now() + 2 * DAY), lifetime: false, store: 'app_store', productId: 'b', willRenew: false });
		expect(await planRow(me)).toMatchObject({ productId: 'b', willRenew: false });
	});
});

describe('handleRevenueCatEvent without a REST key', () => {
	test('syncFromRevenueCat reports that billing is not configured', async () => {
		expect(await syncFromRevenueCat(await fx.user())).toBe(false);
	});

	test('purchase → cancellation → expiration', async () => {
		const me = await fx.user();
		await handleRevenueCatEvent(purchase(me));
		expect(await getPlan(me)).toMatchObject({ tier: 'pro', store: 'app_store', willRenew: true });

		await handleRevenueCatEvent({ type: 'CANCELLATION', app_user_id: me, entitlement_ids: ['pro'] });
		expect(await getPlan(me)).toMatchObject({ tier: 'pro', willRenew: false });

		await handleRevenueCatEvent({ type: 'EXPIRATION', app_user_id: me, entitlement_ids: ['pro'] });
		expect((await getPlan(me)).tier).toBe('free');
	});

	// BUG: see 'lifetime Pro' above; the webhook throws for lifetime purchases.
	test('a lifetime (non-expiring) purchase', async () => {
		const me = await fx.user();
		await handleRevenueCatEvent(purchase(me, { type: 'NON_RENEWING_PURCHASE', expiration_at_ms: null, product_id: 'pro_lifetime', store: 'PLAY_STORE' }));
		expect(await getPlan(me)).toMatchObject({ tier: 'pro', lifetime: true, willRenew: false, store: 'play_store' });
	});

	test('cancellation with no existing plan does nothing', async () => {
		const me = await fx.user();
		await handleRevenueCatEvent({ type: 'CANCELLATION', app_user_id: me, entitlement_ids: ['pro'] });
		expect(await planRow(me)).toBeUndefined();
	});

	test('unknown, anonymous and other-entitlement events are ignored', async () => {
		const me = await fx.user();
		await handleRevenueCatEvent(purchase('not-a-user'));
		await handleRevenueCatEvent(purchase('$RCAnonymousID:abc', { aliases: ['$RCAnonymousID:def'] }));
		await handleRevenueCatEvent(purchase(me, { entitlement_ids: ['something_else'] }));
		await handleRevenueCatEvent(purchase(me, { type: 'BILLING_ISSUE' }));
		expect(await planRow(me)).toBeUndefined();
	});

	test('an anonymous purchase reaches the real user through aliases', async () => {
		const me = await fx.user();
		await handleRevenueCatEvent(purchase('$RCAnonymousID:xyz', { original_app_user_id: '$RCAnonymousID:xyz', aliases: ['$RCAnonymousID:xyz', me] }));
		expect((await getPlan(me)).tier).toBe('pro');
	});

	test('TRANSFER moves Pro from one account to another', async () => {
		const [from, to] = await fx.users(2);
		await handleRevenueCatEvent(purchase(from));
		expect((await getPlan(from)).tier).toBe('pro');

		// RevenueCat's TRANSFER event carries the entitlements being moved.
		await handleRevenueCatEvent({
			type: 'TRANSFER',
			transferred_from: [from],
			transferred_to: [to],
			entitlement_ids: ['pro'],
			expiration_at_ms: Date.now() + 30 * DAY,
			store: 'APP_STORE',
			product_id: 'pro_monthly'
		});
		expect((await getPlan(from)).tier).toBe('free');
		expect(await planRow(from)).toMatchObject({ proUntil: null, lifetime: false });
		// The receiving account gets Pro from the event itself.
		expect(await getPlan(to)).toMatchObject({ tier: 'pro', store: 'app_store' });
	});
});

describe('requireCanCreateGroup', () => {
	test('expired Pro falls back to the free limit', async () => {
		const me = await fx.user();
		await saveEntitlement(me, { proUntil: new Date(Date.now() - 1000), lifetime: false, store: null, productId: null, willRenew: false });
		await requireCanCreateGroup(me);
		for (let i = 0; i < 3; i++) await createGroup(me, { name: `G${i}` }, 'UTC');
		expect((await apiError(requireCanCreateGroup(me))).code).toBe('upgrade_required');
	});
});
