/**
 * What each user wants pushed: categories they've turned off, quiet hours in their own time zone,
 * and groups they've muted. Checked before every push (and the confirmation email); Activity still
 * records everything, so nothing is lost, only not pushed.
 */
import { and, eq, inArray } from 'drizzle-orm';
import {
	NOTIFICATION_CATEGORIES,
	type NotificationCategory,
	type NotificationKind,
	type NotificationSettings
} from '#lib/types';
import { db } from './db';
import { groupMutes, user } from './db/schema';
import { requireMember } from './groups';
import { wallClock } from './availability/time';

/** Which category each kind belongs to; kinds not listed (invites, role changes) always push. */
export const CATEGORY_OF: Partial<Record<NotificationKind, NotificationCategory>> = {
	message: 'chat',
	'proposal.created': 'proposals',
	'proposal.changed': 'proposals',
	'proposal.confirmed': 'plans',
	'event.created': 'plans',
	reminder: 'reminders',
	nudge: 'nudges'
};

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;

const cleanOff = (off: unknown): NotificationCategory[] =>
	Array.isArray(off) ? NOTIFICATION_CATEGORIES.filter((c) => off.includes(c)) : [];
const cleanQuiet = (q: unknown): NotificationSettings['quietHours'] => {
	const v = q as { from?: unknown; to?: unknown } | null;
	return v && typeof v.from === 'string' && typeof v.to === 'string' && TIME.test(v.from) && TIME.test(v.to) && v.from !== v.to
		? { from: v.from, to: v.to }
		: null;
};

export async function getNotificationSettings(userId: string, now = Date.now()): Promise<NotificationSettings> {
	const [row] = await db.select({ off: user.notificationOff, quiet: user.quietHours }).from(user).where(eq(user.id, userId));
	const mutes = await db.select().from(groupMutes).where(eq(groupMutes.userId, userId));
	return {
		off: cleanOff(row?.off),
		quietHours: cleanQuiet(row?.quiet),
		mutes: mutes
			.filter((m) => m.until === null || m.until.getTime() > now)
			.map((m) => ({ groupId: m.groupId, until: m.until?.toISOString() ?? null }))
	};
}

export async function updateNotificationSettings(
	userId: string,
	patch: { off?: NotificationCategory[]; quietHours?: NotificationSettings['quietHours'] }
): Promise<NotificationSettings> {
	const set: Partial<typeof user.$inferInsert> = {};
	if (patch.off !== undefined) set.notificationOff = cleanOff(patch.off);
	if (patch.quietHours !== undefined) set.quietHours = cleanQuiet(patch.quietHours);
	if (Object.keys(set).length) await db.update(user).set(set).where(eq(user.id, userId));
	return getNotificationSettings(userId);
}

/** Mutes a group's pushes for you until `until` (null = until you unmute). */
export async function muteGroup(userId: string, groupId: string, until: string | null): Promise<NotificationSettings> {
	await requireMember(groupId, userId);
	const value = until ? new Date(until) : null;
	await db.insert(groupMutes).values({ userId, groupId, until: value }).onDuplicateKeyUpdate({ set: { until: value } });
	return getNotificationSettings(userId);
}

export async function unmuteGroup(userId: string, groupId: string): Promise<NotificationSettings> {
	await db.delete(groupMutes).where(and(eq(groupMutes.userId, userId), eq(groupMutes.groupId, groupId)));
	return getNotificationSettings(userId);
}

/** Whether `now` falls in quiet hours from–to (which may wrap past midnight) in `timeZone`. */
export function inQuietHours(quiet: NotificationSettings['quietHours'], timeZone: string, now: number): boolean {
	if (!quiet) return false;
	const w = wallClock(now, timeZone);
	const minutes = w.hour * 60 + w.minute;
	const toMinutes = (t: string) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3));
	const from = toMinutes(quiet.from), to = toMinutes(quiet.to);
	return from < to ? minutes >= from && minutes < to : minutes >= from || minutes < to;
}

/** Of `userIds`, who should get a push of `kind` (from `groupId`) right now. */
export async function pushRecipients(userIds: string[], kind: NotificationKind, groupId: string | null, now = Date.now()): Promise<string[]> {
	if (!userIds.length) return [];
	const category = CATEGORY_OF[kind];
	const [prefs, mutes] = await Promise.all([
		db.select({ id: user.id, off: user.notificationOff, quiet: user.quietHours, timezone: user.timezone }).from(user).where(inArray(user.id, userIds)),
		groupId && category
			? db.select().from(groupMutes).where(and(eq(groupMutes.groupId, groupId), inArray(groupMutes.userId, userIds)))
			: Promise.resolve([])
	]);
	const muted = new Set(mutes.filter((m) => m.until === null || m.until.getTime() > now).map((m) => m.userId));
	const byId = new Map(prefs.map((p) => [p.id, p]));
	return userIds.filter((id) => {
		const p = byId.get(id);
		if (!p) return true;
		// Invites and role changes (no category) always come through.
		if (!category) return true;
		if (cleanOff(p.off).includes(category)) return false;
		if (muted.has(id)) return false;
		return !inQuietHours(cleanQuiet(p.quiet), p.timezone || 'UTC', now);
	});
}
