/**
 * Sign-in methods on an account (email, Apple, Google), linking a new one, and
 * merging two accounts when the Apple / Google sign-in being linked already has
 * its own Availabliss account.
 */
import { and, asc, count, eq, sql, type Column } from 'drizzle-orm';
import type { MySqlTable } from 'drizzle-orm/mysql-core';
import { APIError } from 'better-auth';
import { nanoid } from 'nanoid';
import { auth } from '#lib/auth';
import { ROLES, type LinkedAccounts, type LinkProvider, type MergeCandidate } from '#lib/types';
import { db } from './db';
import {
	account,
	busyIntervals,
	calendarConnections,
	devices,
	events,
	groupInvites,
	groupMembers,
	groups,
	manualBlocks,
	messageReceipts,
	messages,
	notifications,
	passkey,
	pollVotes,
	polls,
	proposalResponses,
	proposals,
	requirementTemplates,
	session,
	user,
	userAliases,
	userPlans
} from './db/schema';
import { env } from './env';
import { ApiError, badRequest } from './http';
import { kv } from './redis';

/** Sign-in providers that are configured on this server. */
export function availableProviders(): LinkProvider[] {
	const out: LinkProvider[] = [];
	if (env.apple.clientId && env.apple.clientSecret) out.push('apple');
	if (env.google.clientIds.length && env.google.clientSecret) out.push('google');
	return out;
}

export async function listLinkedAccounts(userId: string): Promise<LinkedAccounts> {
	const rows = await db
		.select({ provider: account.providerId, createdAt: account.createdAt })
		.from(account)
		.where(eq(account.userId, userId))
		.orderBy(asc(account.createdAt));
	const linked = rows
		.filter((r): r is { provider: LinkProvider; createdAt: Date } => r.provider === 'apple' || r.provider === 'google')
		.map((r) => ({ provider: r.provider, linkedAt: r.createdAt.toISOString() }));
	return { linked, available: availableProviders() };
}

/** Enough about the other account for the person to recognise it before merging. */
export async function mergeCandidate(userId: string): Promise<MergeCandidate> {
	const row = await db.query.user.findFirst({ where: eq(user.id, userId), columns: { name: true, email: true } });
	const [{ n }] = await db.select({ n: count() }).from(groupMembers).where(eq(groupMembers.userId, userId));
	return { name: row?.name ?? '', email: row?.email ?? '', groups: n };
}

// ── Merge ────────────────────────────────────────────────────────────────────

/** Every column that points at a user, except user_plans (merged by hand below). */
const USER_COLUMNS: [MySqlTable, Column][] = [
	[session, session.userId],
	[account, account.userId],
	[passkey, passkey.userId],
	[calendarConnections, calendarConnections.userId],
	[busyIntervals, busyIntervals.userId],
	[manualBlocks, manualBlocks.userId],
	[groups, groups.createdBy],
	[groupMembers, groupMembers.userId],
	[groupInvites, groupInvites.createdBy],
	[proposals, proposals.createdBy],
	[proposalResponses, proposalResponses.userId],
	[proposalResponses, proposalResponses.setBy],
	[events, events.createdBy],
	[polls, polls.createdBy],
	[pollVotes, pollVotes.userId],
	[messages, messages.authorId],
	[messageReceipts, messageReceipts.userId],
	[notifications, notifications.userId],
	[devices, devices.userId],
	[requirementTemplates, requirementTemplates.userId],
	[userAliases, userAliases.userId]
];

const roleRank = (role: string) => ROLES.indexOf(role as (typeof ROLES)[number]);

/**
 * Moves everything `fromId` has into `intoId`, then deletes `fromId`.
 *
 * Rows move with UPDATE IGNORE: where both accounts already have the same row
 * (both in a group, both voted on an option) the one from `intoId` stays, and
 * the duplicate goes when `fromId` is deleted. In a group both were in, the
 * stronger role wins. The better Pro plan is kept, and `fromId` is remembered
 * as an alias so RevenueCat events for it still find the account.
 *
 * Sessions move too, so whoever was signed in as `fromId` is now `intoId`.
 */
export async function mergeUsers(fromId: string, intoId: string): Promise<void> {
	if (fromId === intoId) return;
	await db.transaction(async (tx) => {
		const [from, into] = await Promise.all([
			tx.query.user.findFirst({ where: eq(user.id, fromId) }),
			tx.query.user.findFirst({ where: eq(user.id, intoId) })
		]);
		if (!from || !into) throw new ApiError('not_found', 'Account not found');

		// Shared groups: keep the stronger role (an owner stays owner).
		const fromRoles = await tx.select({ groupId: groupMembers.groupId, role: groupMembers.role }).from(groupMembers).where(eq(groupMembers.userId, fromId));
		if (fromRoles.length) {
			const intoRoles = new Map(
				(await tx.select({ groupId: groupMembers.groupId, role: groupMembers.role }).from(groupMembers).where(eq(groupMembers.userId, intoId))).map((r) => [
					r.groupId,
					r.role
				])
			);
			for (const { groupId, role } of fromRoles) {
				const mine = intoRoles.get(groupId);
				if (mine && roleRank(role) < roleRank(mine)) {
					await tx.update(groupMembers).set({ role }).where(and(eq(groupMembers.groupId, groupId), eq(groupMembers.userId, intoId)));
				}
			}
		}

		// Pro: keep whichever plan is better.
		const [fromPlan, intoPlan] = await Promise.all([
			tx.query.userPlans.findFirst({ where: eq(userPlans.userId, fromId) }),
			tx.query.userPlans.findFirst({ where: eq(userPlans.userId, intoId) })
		]);
		if (fromPlan && betterPlan(fromPlan, intoPlan)) {
			const { userId: _, updatedAt: __, ...values } = fromPlan;
			await tx.insert(userPlans).values({ userId: intoId, ...values }).onDuplicateKeyUpdate({ set: values });
		}

		for (const [table, column] of USER_COLUMNS) {
			await tx.execute(sql`UPDATE IGNORE ${table} SET ${column} = ${intoId} WHERE ${column} = ${fromId}`);
		}

		// Fill in what the remaining account doesn't have yet.
		const fill: Partial<typeof user.$inferInsert> = {};
		if (!into.image && from.image) fill.image = from.image;
		if (!into.name.trim() && from.name.trim()) fill.name = from.name;
		if (!into.country && from.country) {
			fill.country = from.country;
			fill.subdivision = from.subdivision;
			fill.schoolAuthority = from.schoolAuthority;
		}
		if (!into.onboarded && from.onboarded) fill.onboarded = true;
		if (Object.keys(fill).length) await tx.update(user).set(fill).where(eq(user.id, intoId));

		await tx.insert(userAliases).values({ oldUserId: fromId, userId: intoId }).onDuplicateKeyUpdate({ set: { userId: intoId } });
		await tx.delete(user).where(eq(user.id, fromId));
	});
}

type PlanRow = typeof userPlans.$inferSelect;

function betterPlan(a: PlanRow, b: PlanRow | undefined): boolean {
	if (!b) return true;
	if (a.lifetime !== b.lifetime) return a.lifetime;
	return (a.proUntil?.getTime() ?? 0) > (b.proUntil?.getTime() ?? 0);
}

// ── Linking from the native apps (id token) ─────────────────────────────────

/** The `sub` claim of a JWT that Better Auth has already verified. */
function subject(idToken: string): string | null {
	try {
		const payload = JSON.parse(Buffer.from(idToken.split('.')[1], 'base64url').toString('utf8'));
		return typeof payload.sub === 'string' ? payload.sub : null;
	} catch {
		return null;
	}
}

export type LinkResult = { linked: true; merged: boolean } | { linked: false; candidate: MergeCandidate };

/**
 * Links an Apple / Google sign-in to the account using an id token from the
 * native SDK. If that sign-in already has its own account, returns it as a
 * candidate unless `merge` is set, in which case it is merged into this one.
 */
export async function linkWithIdToken(
	userId: string,
	headers: Headers,
	input: { provider: LinkProvider; idToken: string; nonce?: string; merge?: boolean }
): Promise<LinkResult> {
	if (!availableProviders().includes(input.provider)) throw badRequest(`${input.provider} sign-in isn't set up on this server`);
	try {
		await auth.api.linkSocialAccount({ headers, body: { provider: input.provider, idToken: { token: input.idToken, nonce: input.nonce } } });
		return { linked: true, merged: false };
	} catch (err) {
		// Better Auth verifies the token before checking who owns it, so a conflict means it was valid.
		if (!(err instanceof APIError) || err.statusCode !== 409) throw err;
	}
	const sub = subject(input.idToken);
	const owner = sub
		? await db.query.account.findFirst({ where: and(eq(account.providerId, input.provider), eq(account.accountId, sub)), columns: { userId: true } })
		: undefined;
	if (!owner) throw new ApiError('conflict', 'That sign-in is already linked to another account');
	if (owner.userId === userId) return { linked: true, merged: false };
	if (!input.merge) return { linked: false, candidate: await mergeCandidate(owner.userId) };
	await mergeUsers(owner.userId, userId);
	return { linked: true, merged: true };
}

// ── Merging on the web (OAuth redirect) ─────────────────────────────────────
// The web links with a redirect, so when the sign-in turns out to have its own
// account there is no token to prove ownership. Instead the person signs in to
// that account through the provider; the intent, bound to this browser by a
// cookie, says which account to merge it into once they're back.

export const MERGE_COOKIE = 'av_merge';
const MERGE_TTL_SECONDS = 10 * 60;

interface MergeIntent {
	intoUserId: string;
	provider: LinkProvider;
	createdAt: number;
}

export async function createMergeIntent(intoUserId: string, provider: LinkProvider): Promise<string> {
	const code = nanoid(32);
	await kv.set(`merge:${code}`, JSON.stringify({ intoUserId, provider, createdAt: Date.now() } satisfies MergeIntent), MERGE_TTL_SECONDS);
	return code;
}

export const mergeCookieOptions = { path: '/', httpOnly: true, sameSite: 'lax' as const, secure: env.baseUrl.startsWith('https:'), maxAge: MERGE_TTL_SECONDS };

/**
 * Finishes a web merge: `current` is who the browser is signed in as now (the
 * other account, just signed in through `provider`). Returns where to go next.
 */
export async function completeMerge(
	code: string | undefined,
	current: { userId: string; sessionCreatedAt: Date } | null
): Promise<{ ok: true; provider: LinkProvider; merged: boolean } | { ok: false; reason: string }> {
	if (!code) return { ok: false, reason: 'merge_expired' };
	const raw = await kv.take(`merge:${code}`);
	if (!raw) return { ok: false, reason: 'merge_expired' };
	const intent = JSON.parse(raw) as MergeIntent;
	if (!current) return { ok: false, reason: 'merge_not_signed_in' };
	if (current.userId === intent.intoUserId) return { ok: true, provider: intent.provider, merged: false };
	// Only a sign-in made after the intent counts (allowing for clock skew between instances).
	if (current.sessionCreatedAt.getTime() < intent.createdAt - 5_000) return { ok: false, reason: 'merge_not_signed_in' };
	const viaProvider = await db.query.account.findFirst({
		where: and(eq(account.userId, current.userId), eq(account.providerId, intent.provider)),
		columns: { id: true }
	});
	if (!viaProvider) return { ok: false, reason: 'merge_wrong_account' };
	await mergeUsers(current.userId, intent.intoUserId);
	return { ok: true, provider: intent.provider, merged: true };
}
