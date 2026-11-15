import { and, asc, desc, eq, gt, inArray } from 'drizzle-orm';
import { alias } from 'drizzle-orm/mysql-core';
import type { Group, Member, Requirement, Role, TileColor } from '#lib/types';
import { TILE_COLORS } from '#lib/types';
import { monogram } from '#lib/monogram';
import { db } from './db';
import { requireCanCreateGroup } from './billing';
import { groupInvites, groupMembers, groups, user } from './db/schema';
import { ApiError, forbidden, notFound } from './http';
import { newId, newToken } from './ids';
import { requirementSummary } from './availability/labels';
import { defaultRequirement } from './requirements';
import { syncedUserIds } from './busy';
import { refreshCoverage } from './calendars';
import { displayName } from './names';

const INVITE_TTL_MS = 14 * 24 * 60 * 60 * 1000;

const isAdmin = (role: Role) => role === 'owner' || role === 'admin';

// ─── Access control ───────────────────────────────────────────────────────

export async function roleIn(groupId: string, userId: string): Promise<Role | null> {
	const row = await db.query.groupMembers.findFirst({
		where: and(eq(groupMembers.groupId, groupId), eq(groupMembers.userId, userId)),
		columns: { role: true }
	});
	return row?.role ?? null;
}

/**
 * Ensures `userId` belongs to the group (404 otherwise, so group ids aren't
 * leaked) and optionally holds an admin or owner role.
 */
export async function requireMember(
	groupId: string,
	userId: string,
	need: 'member' | 'admin' | 'owner' = 'member'
): Promise<Role> {
	const role = await roleIn(groupId, userId);
	if (!role) throw notFound('Group not found');
	if (need === 'admin' && !isAdmin(role)) throw forbidden('Only group admins can do that');
	if (need === 'owner' && role !== 'owner') throw forbidden('Only the group owner can do that');
	return role;
}

export async function groupIdsFor(userId: string): Promise<string[]> {
	const rows = await db.select({ groupId: groupMembers.groupId }).from(groupMembers).where(eq(groupMembers.userId, userId));
	return rows.map((r) => r.groupId);
}

export async function memberIds(groupId: string): Promise<string[]> {
	const rows = await db.select({ userId: groupMembers.userId }).from(groupMembers).where(eq(groupMembers.groupId, groupId));
	return rows.map((r) => r.userId);
}

// ─── Reads ────────────────────────────────────────────────────────────────

export async function listMembers(groupId: string): Promise<Member[]> {
	const rows = await db
		.select({
			userId: groupMembers.userId,
			role: groupMembers.role,
			joinedAt: groupMembers.joinedAt,
			name: user.name,
			email: user.email,
			image: user.image
		})
		.from(groupMembers)
		.innerJoin(user, eq(user.id, groupMembers.userId))
		.where(eq(groupMembers.groupId, groupId))
		.orderBy(asc(groupMembers.joinedAt));
	const synced = await syncedUserIds(rows.map((r) => r.userId));
	return rows.map(({ email, ...r }) => ({
		...r,
		name: displayName({ name: r.name, email }),
		joinedAt: r.joinedAt.toISOString(),
		synced: synced.has(r.userId)
	}));
}

export type GroupRow = typeof groups.$inferSelect;

/** Fields shared by the full group and the groups-list summary. */
export function groupBase(row: GroupRow, role: Role, members: Member[]) {
	return {
		id: row.id,
		name: row.name,
		color: (TILE_COLORS as readonly string[]).includes(row.color) ? (row.color as TileColor) : 't1',
		monogram: monogram(row.name),
		role,
		memberCount: members.length,
		syncedCount: members.filter((m) => m.synced).length,
		requirementSummary: requirementSummary(row.requirement)
	} satisfies Omit<Group, 'requirement' | 'members' | 'createdAt'>;
}

const toGroup = (row: GroupRow, role: Role, members: Member[]): Group => ({
	...groupBase(row, role, members),
	requirement: row.requirement,
	members,
	createdAt: row.createdAt.toISOString()
});

export async function getGroupRow(groupId: string): Promise<GroupRow> {
	const row = await db.query.groups.findFirst({ where: eq(groups.id, groupId) });
	if (!row) throw notFound('Group not found');
	return row;
}

export async function getGroup(groupId: string, userId: string): Promise<Group> {
	const role = await requireMember(groupId, userId);
	const [row, members] = await Promise.all([getGroupRow(groupId), listMembers(groupId)]);
	return toGroup(row, role, members);
}

/** Every group the user belongs to, with their role. */
export async function groupsFor(userId: string): Promise<{ row: GroupRow; role: Role }[]> {
	const rows = await db
		.select({ row: groups, role: groupMembers.role })
		.from(groupMembers)
		.innerJoin(groups, eq(groups.id, groupMembers.groupId))
		.where(eq(groupMembers.userId, userId))
		.orderBy(asc(groupMembers.joinedAt));
	return rows;
}

// ─── Writes ───────────────────────────────────────────────────────────────

export async function createGroup(
	userId: string,
	input: { name: string; color?: TileColor; requirement?: Requirement },
	timeZone: string
): Promise<Group> {
	const id = newId();
	const color = input.color ?? TILE_COLORS[Math.floor(Math.random() * TILE_COLORS.length)];
	await db.transaction(async (tx) => {
		// Locking the user's row queues their parallel creates, so each one counts
		// the groups the others just made before checking the free-plan limit.
		await tx.select({ id: user.id }).from(user).where(eq(user.id, userId)).for('update');
		await requireCanCreateGroup(userId, tx);
		await tx.insert(groups).values({
			id,
			name: input.name,
			color,
			requirement: input.requirement ?? defaultRequirement(timeZone),
			createdBy: userId
		});
		await tx.insert(groupMembers).values({ groupId: id, userId, role: 'owner' });
	});
	if (input.requirement) await refreshCoverage([userId]);
	return getGroup(id, userId);
}

export async function updateGroup(
	groupId: string,
	userId: string,
	patch: { name?: string; color?: TileColor; requirement?: Requirement }
): Promise<Group> {
	await requireMember(groupId, userId, 'admin');
	if (Object.keys(patch).length) await db.update(groups).set(patch).where(eq(groups.id, groupId));
	// The search range may now reach past what members' calendars cover.
	if (patch.requirement) await refreshCoverage(await memberIds(groupId));
	return getGroup(groupId, userId);
}

/** Deletes the group. Returns who was in it, so they can be told. */
export async function deleteGroup(groupId: string, userId: string): Promise<string[]> {
	await requireMember(groupId, userId, 'owner');
	const members = await memberIds(groupId);
	await db.delete(groups).where(eq(groups.id, groupId));
	return members;
}

export async function setMemberRole(groupId: string, actorId: string, targetId: string, role: 'admin' | 'member') {
	await requireMember(groupId, actorId, 'admin');
	const current = await roleIn(groupId, targetId);
	if (!current) throw notFound('Member not found');
	if (current === 'owner') throw forbidden("The owner's role can't be changed");
	await db
		.update(groupMembers)
		.set({ role })
		.where(and(eq(groupMembers.groupId, groupId), eq(groupMembers.userId, targetId)));
	const member = (await listMembers(groupId)).find((m) => m.userId === targetId);
	if (!member) throw notFound('Member not found');
	return member;
}

/** Admins can remove members; anyone can leave. The owner can't leave without deleting the group. */
export async function removeMember(groupId: string, actorId: string, targetId: string): Promise<void> {
	const actorRole = await requireMember(groupId, actorId);
	const targetRole = await roleIn(groupId, targetId);
	if (!targetRole) throw notFound('Member not found');
	if (targetRole === 'owner') throw forbidden('The owner can only delete the group');
	if (actorId !== targetId && !isAdmin(actorRole)) throw forbidden('Only group admins can remove people');
	await db.delete(groupMembers).where(and(eq(groupMembers.groupId, groupId), eq(groupMembers.userId, targetId)));
}

// ─── Invites ──────────────────────────────────────────────────────────────

/** An invite: a link anyone can use, or (with `email` or `invitedUserId`) one only that person can join with. */
export async function createInvite(groupId: string, userId: string, email: string | null, invitedUserId: string | null = null) {
	await requireMember(groupId, userId, 'admin');
	const id = newId();
	const token = newToken();
	const expiresAt = new Date(Date.now() + INVITE_TTL_MS);
	await db
		.insert(groupInvites)
		.values({ id, groupId, token, email: email?.trim().toLowerCase() ?? null, invitedUserId, createdBy: userId, expiresAt });
	return { id, token, expiresAt };
}

/** A group's invites that can still be used, newest first (admins only). */
export async function listInvites(groupId: string, userId: string) {
	await requireMember(groupId, userId, 'admin');
	const invitee = alias(user, 'invitee');
	const rows = await db
		.select({
			id: groupInvites.id,
			token: groupInvites.token,
			email: groupInvites.email,
			username: invitee.username,
			expiresAt: groupInvites.expiresAt,
			createdAt: groupInvites.createdAt,
			inviterName: user.name,
			inviterEmail: user.email
		})
		.from(groupInvites)
		.innerJoin(user, eq(user.id, groupInvites.createdBy))
		.leftJoin(invitee, eq(invitee.id, groupInvites.invitedUserId))
		.where(and(eq(groupInvites.groupId, groupId), gt(groupInvites.expiresAt, new Date())))
		.orderBy(desc(groupInvites.createdAt));
	return rows;
}

/** Revokes an invite so its link stops working (admins only). */
export async function revokeInvite(groupId: string, inviteId: string, userId: string) {
	await requireMember(groupId, userId, 'admin');
	const [result] = await db.delete(groupInvites).where(and(eq(groupInvites.id, inviteId), eq(groupInvites.groupId, groupId)));
	if (result.affectedRows === 0) throw notFound('Invite not found');
}

async function liveInvite(token: string) {
	const invite = await db.query.groupInvites.findFirst({
		where: and(eq(groupInvites.token, token), gt(groupInvites.expiresAt, new Date()))
	});
	if (!invite) throw notFound('This invite has expired or does not exist');
	return invite;
}

export async function previewInvite(token: string) {
	const invite = await liveInvite(token);
	const [group, inviter, members] = await Promise.all([
		getGroupRow(invite.groupId),
		db.query.user.findFirst({ where: eq(user.id, invite.createdBy), columns: { name: true, email: true } }),
		memberIds(invite.groupId)
	]);
	return {
		groupName: group.name,
		inviterName: displayName(inviter),
		memberCount: members.length,
		expiresAt: invite.expiresAt.toISOString(),
		// Who an emailed invite is for (null for a link anyone can use): prefills sign-in.
		email: invite.email,
		// Who it was sent to by username, or null.
		username: invite.invitedUserId
			? ((await db.query.user.findFirst({ where: eq(user.id, invite.invitedUserId), columns: { username: true } }))?.username ?? null)
			: null
	};
}

/**
 * Joins the group. Idempotent, including two taps racing: `joined` is true only
 * for the request that added the membership (an existing member keeps their role).
 */
export async function acceptInvite(token: string, userId: string): Promise<{ groupId: string; joined: boolean }> {
	const invite = await liveInvite(token);
	// An emailed invite is only for the person it was sent to, and is used up once they join.
	if (invite.email) {
		const me = await db.query.user.findFirst({ where: eq(user.id, userId), columns: { email: true } });
		if (me?.email.toLowerCase() !== invite.email.toLowerCase()) {
			throw new ApiError(
				'forbidden',
				`This invite was sent to ${invite.email}, but you're signed in as ${me?.email ?? 'someone else'}. Sign in with ${invite.email} to join.`
			);
		}
	}
	if (invite.invitedUserId && invite.invitedUserId !== userId) {
		const invitee = await db.query.user.findFirst({ where: eq(user.id, invite.invitedUserId), columns: { username: true } });
		const who = invitee?.username ? `@${invitee.username}` : 'someone else';
		throw new ApiError('forbidden', `This invite was sent to ${who}. Sign in as ${who} to join.`);
	}
	const [result] = await db.insert(groupMembers).ignore().values({ groupId: invite.groupId, userId, role: 'member' });
	const joined = result.affectedRows > 0;
	if (invite.email || invite.invitedUserId) await db.delete(groupInvites).where(eq(groupInvites.id, invite.id));
	if (joined) await refreshCoverage([userId]);
	return { groupId: invite.groupId, joined };
}

export async function usersByIds(ids: string[]) {
	if (!ids.length) return [];
	return db.select({ id: user.id, name: user.name, email: user.email }).from(user).where(inArray(user.id, ids));
}
