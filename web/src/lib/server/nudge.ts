/** Admins can remind members who haven't added any busy time yet. */
import { eq } from 'drizzle-orm';
import { db } from './db';
import { groups } from './db/schema';
import { ApiError } from './http';
import { getGroupRow, memberIds, requireMember, usersByIds } from './groups';
import { syncedUserIds } from './busy';
import { notify } from './notifications';
import { sendNudgeEmail } from './email';
import { env } from './env';

const COOLDOWN_MS = 12 * 60 * 60 * 1000;

export async function nudgeUnsynced(groupId: string, userId: string): Promise<{ nudged: number }> {
	await requireMember(groupId, userId, 'admin');
	const group = await getGroupRow(groupId);
	if (group.lastNudgedAt && Date.now() - group.lastNudgedAt.getTime() < COOLDOWN_MS) {
		throw new ApiError('rate_limited', 'This group was nudged recently. Try again later.');
	}

	const ids = await memberIds(groupId);
	const synced = await syncedUserIds(ids);
	const pending = ids.filter((id) => !synced.has(id));
	if (!pending.length) return { nudged: 0 };

	await db.update(groups).set({ lastNudgedAt: new Date() }).where(eq(groups.id, groupId));
	await notify(pending, {
		kind: 'nudge',
		groupId,
		title: group.name,
		body: 'Add your busy times so the group can find a time that works.'
	});
	const people = await usersByIds(pending);
	const url = `${env.baseUrl}/app/my-time`;
	await Promise.allSettled(people.map((p) => sendNudgeEmail({ email: p.email, groupName: group.name, url })));
	return { nudged: pending.length };
}
