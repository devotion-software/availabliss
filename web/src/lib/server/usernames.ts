import { and, eq, isNull } from 'drizzle-orm';
import type { UsernameCheck, UserSummary } from '#lib/types';
import { normalizeUsername, usernameBase, usernameProblem } from '#lib/usernames';
import { db, isDuplicateKey } from './db';
import { user } from './db/schema';
import { ApiError, notFound } from './http';

/**
 * Gives an account without a username one made from its name or email ("samtaylor",
 * then "samtaylor2"…, then random digits), and returns it. Accounts get one when
 * they're created; this also covers accounts from before usernames.
 */
export async function assignUsername(userId: string): Promise<string> {
	const row = await db.query.user.findFirst({ where: eq(user.id, userId), columns: { name: true, email: true, username: true } });
	if (!row) throw notFound('Account not found');
	if (row.username) return row.username;
	const base = usernameBase(row.name, row.email);
	const candidates = [base, ...[2, 3, 4, 5].map((n) => `${base}${n}`)];
	for (let i = 0; i < 20; i++) candidates.push(`${base}${Math.floor(1000 + Math.random() * 9000)}`);
	for (const candidate of candidates) {
		if (usernameProblem(candidate)) continue;
		try {
			const [result] = await db.update(user).set({ username: candidate }).where(and(eq(user.id, userId), isNull(user.username)));
			if (result.affectedRows > 0) return candidate;
			// Someone else assigned one first (two requests at once).
			const now = await db.query.user.findFirst({ where: eq(user.id, userId), columns: { username: true } });
			if (now?.username) return now.username;
		} catch (err) {
			if (!isDuplicateKey(err)) throw err;
		}
	}
	throw new ApiError('server_error', "Couldn't pick a username");
}

/** Someone by username (with or without "@", any case), or null. */
export async function findByUsername(raw: string): Promise<UserSummary | null> {
	const username = normalizeUsername(raw);
	if (usernameProblem(username)) return null;
	const row = await db.query.user.findFirst({
		where: eq(user.username, username),
		columns: { id: true, username: true, name: true, image: true }
	});
	return row?.username ? { id: row.id, username: row.username, name: row.name, image: row.image ?? null } : null;
}

/** Whether `raw` can be your username, and who has it if someone does. */
export async function checkUsername(raw: string, userId: string): Promise<UsernameCheck> {
	const username = normalizeUsername(raw);
	const problem = usernameProblem(username);
	const holder = problem ? null : await findByUsername(username);
	return { username, problem, available: !problem && (!holder || holder.id === userId), user: holder };
}
