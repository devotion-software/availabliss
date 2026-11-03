import { and, asc, eq, inArray, ne } from 'drizzle-orm';
import type { ColorTheme, ThemeMode, User } from '#lib/types';
import { COLOR_THEMES, THEME_MODES } from '#lib/types';
import { db } from './db';
import { groupMembers, groups, user } from './db/schema';
import { notFound } from './http';
import { publishToGroup } from './realtime';
import { onMemberLeft } from './proposals';

type UserRow = typeof user.$inferSelect;

const oneOf = <T extends string>(values: readonly T[], value: string, fallback: T): T =>
	(values as readonly string[]).includes(value) ? (value as T) : fallback;

export const toUser = (row: UserRow): User => ({
	id: row.id,
	name: row.name,
	email: row.email,
	image: row.image ?? null,
	themeMode: oneOf<ThemeMode>(THEME_MODES, row.themeMode, 'system'),
	colorTheme: oneOf<ColorTheme>(COLOR_THEMES, row.colorTheme, 'dawn'),
	country: row.country,
	subdivision: row.subdivision,
	schoolAuthority: row.schoolAuthority,
	timezone: row.timezone,
	onboarded: row.onboarded
});

export async function getMe(userId: string): Promise<User> {
	const row = await db.query.user.findFirst({ where: eq(user.id, userId) });
	if (!row) throw notFound('Account not found');
	return toUser(row);
}

export type UserPatch = Partial<Pick<User, 'name' | 'themeMode' | 'colorTheme' | 'country' | 'subdivision' | 'schoolAuthority' | 'timezone' | 'onboarded'>>;

export async function updateMe(userId: string, patch: UserPatch): Promise<User> {
	if (Object.keys(patch).length) await db.update(user).set(patch).where(eq(user.id, userId));
	return getMe(userId);
}

/**
 * Deletes the account and its data. Groups the user owns pass to the longest-
 * standing remaining member (preferring admins), or are deleted if empty.
 */
export async function deleteAccount(userId: string): Promise<void> {
	const memberOf = (await db.select({ groupId: groupMembers.groupId }).from(groupMembers).where(eq(groupMembers.userId, userId))).map(
		(r) => r.groupId
	);
	await db.transaction(async (tx) => {
		const owned = await tx
			.select({ groupId: groupMembers.groupId })
			.from(groupMembers)
			.where(and(eq(groupMembers.userId, userId), eq(groupMembers.role, 'owner')));

		for (const { groupId } of owned) {
			const [heir] = await tx
				.select({ userId: groupMembers.userId, role: groupMembers.role })
				.from(groupMembers)
				.where(and(eq(groupMembers.groupId, groupId), ne(groupMembers.userId, userId)))
				.orderBy(asc(groupMembers.role), asc(groupMembers.joinedAt))
				.limit(1);
			if (heir) {
				await tx
					.update(groupMembers)
					.set({ role: 'owner' })
					.where(and(eq(groupMembers.groupId, groupId), eq(groupMembers.userId, heir.userId)));
			} else {
				await tx.delete(groups).where(eq(groups.id, groupId));
			}
		}
		await tx.delete(user).where(eq(user.id, userId));
	});

	// As if they'd left each group that still exists: the others see them go, and
	// proposals that were only waiting on them can confirm.
	const remaining = memberOf.length
		? (await db.select({ id: groups.id }).from(groups).where(inArray(groups.id, memberOf))).map((g) => g.id)
		: [];
	for (const groupId of remaining) {
		await publishToGroup(groupId, 'member.changed', { userId, removed: true });
		await onMemberLeft(groupId, userId);
	}
}
