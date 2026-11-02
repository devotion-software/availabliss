/**
 * Plans: free accounts can own a few groups, Pro is unlimited. Purchases happen
 * in the App Store, Play Store or on the web (RevenueCat Web Billing / Stripe);
 * RevenueCat tells us via webhook and we mirror the "pro" entitlement into
 * user_plans. The webhook body is only a hint: we re-read the subscriber from
 * RevenueCat's REST API when a secret key is configured.
 */
import { and, count, eq } from 'drizzle-orm';
import type { Plan } from '#lib/types';
import { db } from './db';
import { groupMembers, user, userAliases, userPlans } from './db/schema';
import { ApiError } from './http';
import { env } from './env';

import { FREE_GROUP_LIMIT } from '#lib/plans';

export { FREE_GROUP_LIMIT };

export interface Entitlement {
	proUntil: Date | null;
	lifetime: boolean;
	store: string | null;
	productId: string | null;
	willRenew: boolean;
}

const FREE: Entitlement = { proUntil: null, lifetime: false, store: null, productId: null, willRenew: false };

export const isActive = (e: Pick<Entitlement, 'proUntil' | 'lifetime'> | null | undefined, now = Date.now()) =>
	!!e && (e.lifetime || (e.proUntil !== null && e.proUntil.getTime() > now));

async function entitlementOf(userId: string): Promise<Entitlement | null> {
	return (await db.query.userPlans.findFirst({ where: eq(userPlans.userId, userId) })) ?? null;
}

export async function isPro(userId: string): Promise<boolean> {
	return isActive(await entitlementOf(userId));
}

async function groupsOwned(userId: string): Promise<number> {
	const [row] = await db
		.select({ n: count() })
		.from(groupMembers)
		.where(and(eq(groupMembers.userId, userId), eq(groupMembers.role, 'owner')));
	return row?.n ?? 0;
}

export async function getPlan(userId: string): Promise<Plan> {
	const [entitlement, owned] = await Promise.all([entitlementOf(userId), groupsOwned(userId)]);
	const pro = isActive(entitlement);
	const limit = pro ? null : FREE_GROUP_LIMIT;
	return {
		tier: pro ? 'pro' : 'free',
		proUntil: pro && !entitlement!.lifetime ? entitlement!.proUntil!.toISOString() : null,
		lifetime: pro && entitlement!.lifetime,
		willRenew: pro && entitlement!.willRenew,
		store: pro ? entitlement!.store : null,
		groupLimit: limit,
		groupsOwned: owned,
		canCreateGroup: limit === null || owned < limit,
		aiPlanner: { available: !!env.ai.apiKey, included: pro },
		purchase: {
			ios: env.revenuecat.iosKey,
			android: env.revenuecat.androidKey,
			web: env.revenuecat.webKey,
			entitlement: env.revenuecat.entitlement
		}
	};
}

/** Throws 402 upgrade_required when a free account already owns its limit of groups. */
export async function requireCanCreateGroup(userId: string): Promise<void> {
	if (await isPro(userId)) return;
	if ((await groupsOwned(userId)) < FREE_GROUP_LIMIT) return;
	throw new ApiError(
		'upgrade_required',
		`Free accounts can create up to ${FREE_GROUP_LIMIT} groups. Upgrade to Pro for unlimited groups, or delete one you no longer need.`
	);
}

export async function saveEntitlement(userId: string, e: Entitlement): Promise<void> {
	// Lifetime has no expiry: proUntil stays null and the flag carries it (a far-future
	// date would overflow MariaDB's TIMESTAMP range).
	const values = { ...e, proUntil: e.lifetime ? null : e.proUntil };
	await db.insert(userPlans).values({ userId, ...values }).onDuplicateKeyUpdate({ set: values });
}

// ─── RevenueCat ───────────────────────────────────────────────────────────

/** The parts of GET /v1/subscribers/:id we read. */
export interface RcSubscriber {
	entitlements?: Record<string, { expires_date: string | null; product_identifier: string } | undefined>;
	subscriptions?: Record<string, { store?: string; unsubscribe_detected_at?: string | null; expires_date?: string | null } | undefined>;
	non_subscriptions?: Record<string, { store?: string }[] | undefined>;
}

/** Turns a RevenueCat subscriber into our entitlement. Expired entitlements read as free. */
export function entitlementFromSubscriber(subscriber: RcSubscriber, entitlementId: string, now = Date.now()): Entitlement {
	const ent = subscriber.entitlements?.[entitlementId];
	if (!ent) return FREE;
	const product = ent.product_identifier;
	const sub = subscriber.subscriptions?.[product];
	const store = sub?.store ?? subscriber.non_subscriptions?.[product]?.[0]?.store ?? null;
	if (ent.expires_date === null) return { proUntil: null, lifetime: true, store, productId: product, willRenew: false };
	const proUntil = new Date(ent.expires_date);
	if (proUntil.getTime() <= now) return FREE;
	return { proUntil, lifetime: false, store, productId: product, willRenew: !sub?.unsubscribe_detected_at };
}

// TRANSFER grants the receiving account; the giving one is downgraded separately.
const GRANTING = new Set(['INITIAL_PURCHASE', 'RENEWAL', 'UNCANCELLATION', 'PRODUCT_CHANGE', 'NON_RENEWING_PURCHASE', 'SUBSCRIPTION_EXTENDED', 'TEMPORARY_ENTITLEMENT_GRANT', 'TRANSFER']);

export interface RcEvent {
	type: string;
	app_user_id?: string;
	original_app_user_id?: string;
	aliases?: string[];
	transferred_from?: string[];
	transferred_to?: string[];
	entitlement_ids?: string[] | null;
	expiration_at_ms?: number | null;
	store?: string;
	product_id?: string;
}

/** Fallback when no REST key is configured: read the entitlement straight from the event. */
export function entitlementFromEvent(event: RcEvent, entitlementId: string, current: Entitlement | null): Entitlement | null {
	if (!(event.entitlement_ids ?? []).includes(entitlementId)) return null;
	if (event.type === 'EXPIRATION') return FREE;
	if (event.type === 'CANCELLATION') return current ? { ...current, willRenew: false } : null;
	if (!GRANTING.has(event.type)) return null;
	const lifetime = event.expiration_at_ms == null;
	return {
		proUntil: lifetime ? null : new Date(event.expiration_at_ms!),
		lifetime,
		store: event.store?.toLowerCase() ?? null,
		productId: event.product_id ?? null,
		willRenew: event.type !== 'NON_RENEWING_PURCHASE'
	};
}

async function fetchSubscriber(appUserId: string): Promise<RcSubscriber> {
	const res = await fetch(`https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(appUserId)}`, {
		headers: { authorization: `Bearer ${env.revenuecat.secretKey}`, accept: 'application/json' }
	});
	if (!res.ok) throw new Error(`RevenueCat ${res.status}: ${await res.text()}`);
	return ((await res.json()) as { subscriber: RcSubscriber }).subscriber;
}

/** Our users are RevenueCat app users with the same id; anonymous ids are ignored. */
async function knownUser(id: string | undefined): Promise<string | null> {
	if (!id || id.startsWith('$RCAnonymousID')) return null;
	const row = await db.query.user.findFirst({ where: eq(user.id, id), columns: { id: true } });
	if (row) return row.id;
	// An account merged into another one: its purchases now belong to that account.
	const alias = await db.query.userAliases.findFirst({ where: eq(userAliases.oldUserId, id), columns: { userId: true } });
	return alias?.userId ?? null;
}

/** Re-reads a user's entitlement from RevenueCat. Returns false when billing isn't configured. */
export async function syncFromRevenueCat(userId: string): Promise<boolean> {
	if (!env.revenuecat.secretKey) return false;
	await saveEntitlement(userId, entitlementFromSubscriber(await fetchSubscriber(userId), env.revenuecat.entitlement));
	return true;
}

export async function handleRevenueCatEvent(event: RcEvent): Promise<void> {
	const ids = new Set<string>();
	for (const id of [event.app_user_id, event.original_app_user_id, ...(event.aliases ?? []), ...(event.transferred_from ?? []), ...(event.transferred_to ?? [])]) {
		const known = await knownUser(id);
		if (known) ids.add(known);
	}
	for (const userId of ids) {
		if (await syncFromRevenueCat(userId)) continue;
		const next = entitlementFromEvent(event, env.revenuecat.entitlement, await entitlementOf(userId));
		if (event.type === 'TRANSFER' && (event.transferred_from ?? []).includes(userId)) await saveEntitlement(userId, FREE);
		else if (next) await saveEntitlement(userId, next);
	}
}
