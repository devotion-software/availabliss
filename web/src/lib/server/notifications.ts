import { and, desc, eq, inArray, isNull, ne, sql } from 'drizzle-orm';
import type { AppNotification, NotificationKind } from '#lib/types';
import { db } from './db';
import { devices, notifications } from './db/schema';
import { newId } from './ids';
import { publishToUsers } from './realtime';
import { sendPush } from './push';
import { pushRecipients } from './notification-settings';
import { log } from './log';

interface NotifyInput {
	kind: NotificationKind;
	groupId: string | null;
	title: string;
	body: string;
	/** Deep-link data (proposalId, eventId, messageId …). */
	data?: Record<string, string>;
	/**
	 * true (default): a new Activity item. 'per-group' (chat): one item per group,
	 * brought to the top with each message and unread until the chat is read
	 * (`markChatRead`), with `data.count` messages since. false: push only.
	 */
	record?: boolean | 'per-group';
	/** false: Activity item only, no push (e.g. a note of something you did yourself). Default true. */
	push?: boolean;
	/** true: the Activity item is recorded already read, so it doesn't count as unread. */
	read?: boolean;
}

type Row = typeof notifications.$inferSelect;

const toNotification = (row: Row): AppNotification => ({
	id: row.id,
	groupId: row.groupId,
	kind: row.kind,
	title: row.title,
	body: row.body,
	data: row.data,
	readAt: row.readAt?.toISOString() ?? null,
	createdAt: row.createdAt.toISOString()
});

/** Records an Activity item, publishes it live, and sends a push to each user. */
export async function notify(userIds: string[], input: NotifyInput): Promise<void> {
	if (!userIds.length) return;
	const data = { ...input.data, kind: input.kind, ...(input.groupId && { groupId: input.groupId }) };

	if (input.record === 'per-group' && input.groupId) {
		await upsertPerGroup(userIds, input.groupId, input, data);
	} else if (input.record !== false) {
		const now = new Date();
		const rows = userIds.map((userId) => ({
			id: newId(),
			userId,
			groupId: input.groupId,
			kind: input.kind,
			title: input.title,
			body: input.body,
			data,
			readAt: input.read ? now : null,
			createdAt: now
		}));
		await db.insert(notifications).values(rows);
		for (const row of rows) {
			publishToUsers([row.userId], {
				type: 'notification.created',
				groupId: row.groupId,
				payload: toNotification(row)
			});
		}
	}

	// Push is best effort and shouldn't hold up the request.
	// Leaves out categories people turned off, muted groups and quiet hours (Activity still has it).
	if (input.push !== false) {
		void pushRecipients(userIds, input.kind, input.groupId, Date.now())
			.then((to) => sendPush(to, { title: input.title, body: input.body, data }))
			.catch((err) => log.warn('push', 'choosing recipients failed', { err }));
	}
}

/** The group's one Activity item for each user, created or brought up to date (see `record`). */
async function upsertPerGroup(userIds: string[], groupId: string, input: NotifyInput, data: Record<string, string>) {
	const existing = await db
		.select()
		.from(notifications)
		.where(and(inArray(notifications.userId, userIds), eq(notifications.groupId, groupId), eq(notifications.kind, input.kind)));
	const byUser = new Map(existing.map((row) => [row.userId, row]));
	const now = new Date();
	for (const userId of userIds) {
		const prev = byUser.get(userId);
		const count = prev && !prev.readAt ? (Number(prev.data.count) || 1) + 1 : 1;
		const row: Row = {
			id: prev?.id ?? newId(),
			userId,
			groupId,
			kind: input.kind,
			title: input.title,
			body: input.body,
			data: { ...data, count: String(count) },
			readAt: null,
			createdAt: now
		};
		if (prev) {
			await db
				.update(notifications)
				.set({ title: row.title, body: row.body, data: row.data, readAt: null, createdAt: now })
				.where(eq(notifications.id, prev.id));
		} else {
			await db.insert(notifications).values(row);
		}
		publishToUsers([userId], { type: 'notification.created', groupId, payload: toNotification(row) });
	}
}

/** Reading a group's chat reads its Activity item; the user's other devices are told. */
export async function markChatRead(userId: string, groupId: string): Promise<void> {
	const unread = await db
		.select()
		.from(notifications)
		.where(and(eq(notifications.userId, userId), eq(notifications.groupId, groupId), eq(notifications.kind, 'message'), isNull(notifications.readAt)));
	if (!unread.length) return;
	const readAt = new Date();
	await db.update(notifications).set({ readAt }).where(inArray(notifications.id, unread.map((r) => r.id)));
	for (const row of unread) {
		publishToUsers([userId], { type: 'notification.created', groupId, payload: toNotification({ ...row, readAt }) });
	}
}

/** Removes the Activity item for an invite sent to you (`invite.received` with that token). */
export async function removeInviteNotification(userId: string, token: string): Promise<void> {
	await db
		.delete(notifications)
		.where(
			and(
				eq(notifications.userId, userId),
				eq(notifications.kind, 'invite.received'),
				sql`json_unquote(json_extract(${notifications.data}, '$.token')) = ${token}`
			)
		);
}

export async function listNotifications(userId: string) {
	const [rows, [{ unread }]] = await Promise.all([
		db.select().from(notifications).where(eq(notifications.userId, userId)).orderBy(desc(notifications.createdAt)).limit(100),
		db
			.select({ unread: sql<number>`count(*)` })
			.from(notifications)
			.where(and(eq(notifications.userId, userId), isNull(notifications.readAt)))
	]);
	return { notifications: rows.map(toNotification), unread: Number(unread) };
}

/**
 * Marks the given notifications read, or all of them except chats, which stay
 * unread until the chat itself is read (`markChatRead`).
 */
export async function markRead(userId: string, ids?: string[]): Promise<void> {
	const scope = ids?.length ? inArray(notifications.id, ids) : ne(notifications.kind, 'message');
	await db
		.update(notifications)
		.set({ readAt: new Date() })
		.where(and(eq(notifications.userId, userId), isNull(notifications.readAt), scope));
}

export async function registerDevice(userId: string, input: { platform: 'ios' | 'android'; token: string; name: string | null }) {
	await db
		.insert(devices)
		.values({ id: newId(), userId, ...input })
		// A token moves to whoever signed in on the device most recently.
		.onDuplicateKeyUpdate({ set: { userId, platform: input.platform, name: input.name, updatedAt: new Date() } });
}

export async function unregisterDevice(userId: string, token: string): Promise<void> {
	await db.delete(devices).where(and(eq(devices.userId, userId), eq(devices.token, token)));
}
