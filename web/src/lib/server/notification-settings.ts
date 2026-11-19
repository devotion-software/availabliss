/**
 * What each user wants pushed: categories they've turned off, quiet hours in their own time zone,
 * and groups they've muted. Checked before every push (and the confirmation email); Activity still
 * records everything, so nothing is lost, only not pushed.
 */
import { and, eq, gte, inArray, isNotNull, isNull, notInArray, or } from 'drizzle-orm';
import { NOTIFICATION_CATEGORIES, type NotificationCategory, type NotificationKind, type NotificationSettings } from '#lib/types';
import { db } from './db';
import { groupMutes, groups, notifications, user } from './db/schema';
import { sendPush } from './push';
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

const cleanOff = (off: unknown): NotificationCategory[] => (Array.isArray(off) ? NOTIFICATION_CATEGORIES.filter((c) => off.includes(c)) : []);
const cleanQuiet = (q: unknown): NotificationSettings['quietHours'] => {
	const v = q as { from?: unknown; to?: unknown; catchUp?: unknown } | null;
	return v && typeof v.from === 'string' && typeof v.to === 'string' && TIME.test(v.from) && TIME.test(v.to) && v.from !== v.to
		? { from: v.from, to: v.to, catchUp: v.catchUp === true }
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
	patch: { off?: NotificationCategory[]; quietHours?: { from: string; to: string; catchUp?: boolean } | null }
): Promise<NotificationSettings> {
	const set: Partial<typeof user.$inferInsert> = {};
	if (patch.off !== undefined) set.notificationOff = cleanOff(patch.off);
	if (patch.quietHours !== undefined) {
		// Left out (an older app changing the times), catching up stays as it was.
		const catchUp = patch.quietHours?.catchUp ?? (await getNotificationSettings(userId)).quietHours?.catchUp ?? false;
		set.quietHours = cleanQuiet(patch.quietHours && { ...patch.quietHours, catchUp });
		// Nothing's owed once quiet hours (or catching up) are off.
		if (!set.quietHours?.catchUp) set.quietHeldSince = null;
	}
	if (Object.keys(set).length) await db.update(user).set(set).where(eq(user.id, userId));
	return getNotificationSettings(userId);
}

/** Mutes a group's pushes for you until `until` (null = until you unmute). */
export async function muteGroup(userId: string, groupId: string, until: string | null): Promise<NotificationSettings> {
	await requireMember(groupId, userId);
	const value = until ? new Date(until) : null;
	await db
		.insert(groupMutes)
		.values({ userId, groupId, until: value })
		.onDuplicateKeyUpdate({ set: { until: value } });
	return getNotificationSettings(userId);
}

export async function unmuteGroup(userId: string, groupId: string): Promise<NotificationSettings> {
	await db.delete(groupMutes).where(and(eq(groupMutes.userId, userId), eq(groupMutes.groupId, groupId)));
	return getNotificationSettings(userId);
}

/** Whether `now` falls in quiet hours from–to (which may wrap past midnight) in `timeZone`. */
export function inQuietHours(quiet: { from: string; to: string } | null, timeZone: string, now: number): boolean {
	if (!quiet) return false;
	const w = wallClock(now, timeZone);
	const minutes = w.hour * 60 + w.minute;
	const toMinutes = (t: string) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3));
	const from = toMinutes(quiet.from),
		to = toMinutes(quiet.to);
	return from < to ? minutes >= from && minutes < to : minutes >= from || minutes < to;
}

/**
 * Of `userIds`, who should get a push of `kind` (from `groupId`) right now. Someone held back only by
 * quiet hours, with catch-up on, is noted for the summary push when they end (see sendQuietHoursCatchUps).
 */
export async function pushRecipients(userIds: string[], kind: NotificationKind, groupId: string | null, now = Date.now()): Promise<string[]> {
	if (!userIds.length) return [];
	const category = CATEGORY_OF[kind];
	const [prefs, mutes] = await Promise.all([
		db
			.select({ id: user.id, off: user.notificationOff, quiet: user.quietHours, timezone: user.timezone, heldSince: user.quietHeldSince })
			.from(user)
			.where(inArray(user.id, userIds)),
		groupId && category
			? db
					.select()
					.from(groupMutes)
					.where(and(eq(groupMutes.groupId, groupId), inArray(groupMutes.userId, userIds)))
			: Promise.resolve([])
	]);
	const muted = new Set(mutes.filter((m) => m.until === null || m.until.getTime() > now).map((m) => m.userId));
	const byId = new Map(prefs.map((p) => [p.id, p]));
	const held: string[] = [];
	const to = userIds.filter((id) => {
		const p = byId.get(id);
		if (!p) return true;
		// Invites and role changes (no category) always come through.
		if (!category) return true;
		if (cleanOff(p.off).includes(category)) return false;
		if (muted.has(id)) return false;
		const quiet = cleanQuiet(p.quiet);
		if (!inQuietHours(quiet, p.timezone || 'UTC', now)) return true;
		if (quiet?.catchUp && !p.heldSince) held.push(id);
		return false;
	});
	if (held.length) {
		await db
			.update(user)
			.set({ quietHeldSince: new Date(now) })
			.where(and(inArray(user.id, held), isNull(user.quietHeldSince)));
	}
	return to;
}

/** Most groups a catch-up push names before "and N more". */
const CATCH_UP_GROUPS = 2;

/** "1 update", "4 updates in Lake District crew", "6 updates in Lake District crew, Pub quiz and 1 more". */
export function catchUpBody(count: number, groupNames: string[]): string {
	const shown = groupNames.slice(0, CATCH_UP_GROUPS);
	const more = groupNames.length - shown.length;
	const where = shown.length ? ` in ${more ? `${shown.join(', ')} and ${more} more` : shown.join(' and ')}` : '';
	return `${count} ${count === 1 ? 'update' : 'updates'}${where}`;
}

/**
 * The push owed to each person whose quiet hours have ended with something held back: what's still
 * unread in Activity from then (in categories they get and groups they haven't muted), e.g. "4 updates
 * in Lake District crew and Pub quiz". Nothing is sent when they've read it all meanwhile.
 */
export async function sendQuietHoursCatchUps(now = Date.now()): Promise<string[]> {
	const owed = await db
		.select({ id: user.id, quiet: user.quietHours, timezone: user.timezone, heldSince: user.quietHeldSince, off: user.notificationOff })
		.from(user)
		.where(isNotNull(user.quietHeldSince));
	const sent: string[] = [];
	for (const p of owed) {
		if (inQuietHours(cleanQuiet(p.quiet), p.timezone || 'UTC', now)) continue;
		// Claims it first, so a second instance (or the next run) doesn't send it again.
		const claimed = await db
			.update(user)
			.set({ quietHeldSince: null })
			.where(and(eq(user.id, p.id), eq(user.quietHeldSince, p.heldSince!)));
		if (!claimed[0]?.affectedRows) continue;
		const off = cleanOff(p.off);
		const kinds = (Object.entries(CATEGORY_OF) as [NotificationKind, NotificationCategory][]).filter(([, c]) => !off.includes(c)).map(([k]) => k);
		if (!kinds.length) continue;
		const mutedNow = (await db.select().from(groupMutes).where(eq(groupMutes.userId, p.id)))
			.filter((m) => m.until === null || m.until.getTime() > now)
			.map((m) => m.groupId);
		const rows = await db
			.select({ groupId: notifications.groupId, data: notifications.data, name: groups.name })
			.from(notifications)
			.leftJoin(groups, eq(groups.id, notifications.groupId))
			.where(
				and(
					eq(notifications.userId, p.id),
					isNull(notifications.readAt),
					gte(notifications.createdAt, p.heldSince!),
					inArray(notifications.kind, kinds),
					mutedNow.length ? or(isNull(notifications.groupId), notInArray(notifications.groupId, mutedNow)) : undefined
				)
			);
		// A chat's one Activity item counts each message it stands for.
		const count = rows.reduce((n, r) => n + (Number(r.data?.count) || 1), 0);
		if (!count) continue;
		const names = [...new Set(rows.flatMap((r) => (r.name ? [r.name] : [])))];
		const groupId = new Set(rows.map((r) => r.groupId)).size === 1 ? rows[0].groupId : null;
		await sendPush([p.id], {
			title: 'While it was quiet',
			body: catchUpBody(count, names),
			data: { kind: 'quiet.summary', ...(groupId && { groupId }) }
		});
		sent.push(p.id);
	}
	return sent;
}
