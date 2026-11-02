import { describe, expect, test } from 'bun:test';
import { entitlementFromEvent, entitlementFromSubscriber, isActive, type Entitlement } from './billing';

const NOW = Date.parse('2026-11-02T12:00:00Z');

describe('entitlementFromSubscriber', () => {
	test('active subscription', () => {
		const e = entitlementFromSubscriber(
			{
				entitlements: { pro: { expires_date: '2026-12-02T12:00:00Z', product_identifier: 'pro_monthly' } },
				subscriptions: { pro_monthly: { store: 'app_store', unsubscribe_detected_at: null } }
			},
			'pro',
			NOW
		);
		expect(e).toEqual({ proUntil: new Date('2026-12-02T12:00:00Z'), lifetime: false, store: 'app_store', productId: 'pro_monthly', willRenew: true });
		expect(isActive(e, NOW)).toBe(true);
	});

	test('cancelled but not yet expired stays Pro without renewing', () => {
		const e = entitlementFromSubscriber(
			{
				entitlements: { pro: { expires_date: '2026-11-20T00:00:00Z', product_identifier: 'pro_annual' } },
				subscriptions: { pro_annual: { store: 'stripe', unsubscribe_detected_at: '2026-11-01T00:00:00Z' } }
			},
			'pro',
			NOW
		);
		expect(isActive(e, NOW)).toBe(true);
		expect(e.willRenew).toBe(false);
		expect(e.store).toBe('stripe');
	});

	test('expired or missing entitlement is free', () => {
		const expired = entitlementFromSubscriber({ entitlements: { pro: { expires_date: '2026-10-01T00:00:00Z', product_identifier: 'x' } } }, 'pro', NOW);
		expect(isActive(expired, NOW)).toBe(false);
		expect(isActive(entitlementFromSubscriber({ entitlements: {} }, 'pro', NOW), NOW)).toBe(false);
		expect(isActive(entitlementFromSubscriber({ entitlements: { other: { expires_date: null, product_identifier: 'x' } } }, 'pro', NOW), NOW)).toBe(false);
	});

	test('lifetime purchase', () => {
		const e = entitlementFromSubscriber(
			{ entitlements: { pro: { expires_date: null, product_identifier: 'pro_lifetime' } }, non_subscriptions: { pro_lifetime: [{ store: 'play_store' }] } },
			'pro',
			NOW
		);
		expect(e).toMatchObject({ lifetime: true, store: 'play_store', willRenew: false });
		expect(isActive(e, NOW)).toBe(true);
	});
});

describe('entitlementFromEvent (no REST key)', () => {
	const current: Entitlement = { proUntil: new Date('2026-12-01T00:00:00Z'), lifetime: false, store: 'app_store', productId: 'm', willRenew: true };

	test('purchases and renewals grant until expiry', () => {
		const e = entitlementFromEvent({ type: 'RENEWAL', entitlement_ids: ['pro'], expiration_at_ms: Date.parse('2027-01-01T00:00:00Z'), store: 'APP_STORE', product_id: 'm' }, 'pro', null);
		expect(e).toEqual({ proUntil: new Date('2027-01-01T00:00:00Z'), lifetime: false, store: 'app_store', productId: 'm', willRenew: true });
	});

	test('cancellation keeps access but stops renewal; expiration ends it', () => {
		expect(entitlementFromEvent({ type: 'CANCELLATION', entitlement_ids: ['pro'] }, 'pro', current)).toEqual({ ...current, willRenew: false });
		expect(isActive(entitlementFromEvent({ type: 'EXPIRATION', entitlement_ids: ['pro'] }, 'pro', current), NOW)).toBe(false);
	});

	test('events for other entitlements or informational types are ignored', () => {
		expect(entitlementFromEvent({ type: 'RENEWAL', entitlement_ids: ['other'], expiration_at_ms: 1 }, 'pro', null)).toBeNull();
		expect(entitlementFromEvent({ type: 'BILLING_ISSUE', entitlement_ids: ['pro'] }, 'pro', current)).toBeNull();
	});
});
