import { and, asc, eq, gt, inArray } from 'drizzle-orm';
import type { Group, Member, Requirement, Role, TileColor } from '#lib/types';
import { TILE_COLORS } from '#lib/types';
import { db } from './db';
import { groupInvites, groupMembers, groups, user } from './db/schema';
import { forbidden, notFound } from './http';
import { newId, newToken } from './ids';
import { requirementSummary } from './availability/labels';
import { defaultRequirement } from './requirements';
import { syncedUserIds } from './busy';

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

/** "Lake District crew" → "LD", "Book club" → "BC", "Five" → "F". */
export function monogram(name: string): string {
	const words = name.trim().split(/\s+/).filter((w) => /[\p{L}\p{N}]/u.test(w));
	const letters = words.length > 1 ? words.slice(0, 2).map((w) => [...w][0]) : [...(words[0] ?? '?')].slice(0, 2);
	return letters.join('').toUpperCase();
}

export async function listMembers(groupId: string): Promise<Member[]> {
	const rows = await db
		.select({
			userId: groupMembers.userId,
			role: groupMembers.role,
			joinedAt: groupMembers.joinedAt,
			name: user.name,
			image: user.image
		})
		.from(groupMembers)
		.innerJoin(user, eq(user.id, groupMembers.userId))
		.where(eq(groupMembers.groupId, groupId))
		.orderBy(asc(groupMembers.joinedAt));
	const synced = await syncedUserIds(rows.map((r) => r.userId));
	return rows.map((r) => ({ ...r, joinedAt: r.joinedAt.toISOString(), synced: synced.has(r.userId) }));
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
		await tx.insert(groups).values({
			id,
			name: input.name,
			color,
			requirement: input.requirement ?? defaultRequirement(timeZone),
			createdBy: userId
		});
		await tx.insert(groupMembers).values({ groupId: id, userId, role: 'owner' });
	});
	return getGroup(id, userId);
}

export async function updateGroup(
	groupId: string,
	userId: string,
	patch: { name?: string; color?: TileColor; requirement?: Requirement }
): Promise<Group> {
	await requireMember(groupId, userId, 'admin');
	if (Object.keys(patch).length) await db.update(groups).set(patch).where(eq(groups.id, groupId));
	return getGroup(groupId, userId);
}

export async function deleteGroup(groupId: string, userId: string): Promise<void> {
	await requireMember(groupId, userId, 'owner');
	await db.delete(groups).where(eq(groups.id, groupId));
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

export async function createInvite(groupId: string, userId: string, email: string | null) {
	await requireMember(groupId, userId, 'admin');
	const token = newToken();
	const expiresAt = new Date(Date.now() + INVITE_TTL_MS);
	await db.insert(groupInvites).values({ id: newId(), groupId, token, email, createdBy: userId, expiresAt });
	return { token, expiresAt };
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
		db.query.user.findFirst({ where: eq(user.id, invite.createdBy), columns: { name: true } }),
		memberIds(invite.groupId)
	]);
	return {
		groupName: group.name,
		inviterName: inviter?.name ?? 'Someone',
		memberCount: members.length,
		expiresAt: invite.expiresAt.toISOString()
	};
}

/** Joins the group (idempotent). Returns the group id. */
export async function acceptInvite(token: string, userId: string): Promise<string> {
	const invite = await liveInvite(token);
	if (!(await roleIn(invite.groupId, userId))) {
		await db.insert(groupMembers).values({ groupId: invite.groupId, userId, role: 'member' });
	}
	return invite.groupId;
}

export async function usersByIds(ids: string[]) {
	if (!ids.length) return [];
	return db.select({ id: user.id, name: user.name, email: user.email }).from(user).where(inArray(user.id, ids));
}
