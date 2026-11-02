/**
 * Test data for the DB integration tests. Every file makes its own fixture, creates
 * users with fresh ids and removes everything it made in afterAll, so the tests can
 * run against a dev database without touching the demo data.
 */
import './setup';
import { inArray } from 'drizzle-orm';
import type { Requirement } from '#lib/types';
import { db } from '#lib/server/db';
import { groupMembers, groups, user } from '#lib/server/db/schema';
import { newId } from '#lib/server/ids';
import { ApiError } from '#lib/server/http';

/** The ApiError a call rejects with; fails the test if it resolves or throws something else. */
export async function apiError(p: Promise<unknown>): Promise<ApiError> {
	try {
		await p;
	} catch (err) {
		if (err instanceof ApiError) return err;
		throw err;
	}
	throw new Error('Expected an ApiError');
}

export function fixture(prefix: string) {
	const userIds: string[] = [];
	let lastJoin = 0;

	return {
		userIds,

		async user(overrides: Partial<typeof user.$inferInsert> = {}): Promise<string> {
			const id = newId();
			userIds.push(id);
			await db.insert(user).values({
				id,
				name: `${prefix} ${userIds.length}`,
				email: `it-${prefix}-${id}@test.availabliss.invalid`,
				timezone: 'UTC',
				...overrides
			});
			return id;
		},

		async users(n: number): Promise<string[]> {
			const ids: string[] = [];
			for (let i = 0; i < n; i++) ids.push(await this.user());
			return ids;
		},

		/**
		 * Adds a member straight to the table (no invite round trip). joined_at has ms
		 * precision, so each join is stamped at least 1ms after the last (and after
		 * the owner) to keep join order deterministic.
		 */
		async join(groupId: string, userId: string, role: 'admin' | 'member' = 'member') {
			lastJoin = Math.max(Date.now() + 1, lastJoin + 1);
			await db.insert(groupMembers).values({ groupId, userId, role, joinedAt: new Date(lastJoin) });
		},

		/**
		 * Groups only lose `created_by` when a user is deleted, so delete the test
		 * users' groups first; everything else cascades from the user rows.
		 */
		async cleanup() {
			if (!userIds.length) return;
			const memberOf = await db.select({ id: groupMembers.groupId }).from(groupMembers).where(inArray(groupMembers.userId, userIds));
			const created = await db.select({ id: groups.id }).from(groups).where(inArray(groups.createdBy, userIds));
			const groupIds = [...new Set([...memberOf, ...created].map((r) => r.id))];
			if (groupIds.length) await db.delete(groups).where(inArray(groups.id, groupIds));
			await db.delete(user).where(inArray(user.id, userIds));
		}
	};
}

/** A whole-day, every-day timing over a fixed future range, in UTC. */
export function everyDay(from = '2031-03-03', to = '2031-03-09'): Requirement {
	return {
		length: { type: 'days', count: 1 },
		window: null,
		days: [0, 1, 2, 3, 4, 5, 6],
		bankHolidays: false,
		schoolHolidaysOnly: false,
		schoolAuthority: null,
		customSchoolHolidays: [],
		range: { from, to },
		dates: [],
		timezone: 'UTC'
	};
}
