import { and, desc, eq, inArray, isNull, sql } from 'drizzle-orm';
import type { AppNotification, NotificationKind } from '#lib/types';
import { db } from './db';
import { devices, notifications } from './db/schema';
import { newId } from './ids';
import { publishToUsers } from './realtime';
import { sendPush } from './push';

interface NotifyInput {
	kind: NotificationKind;
	groupId: string | null;
	title: string;
	body: string;
	/** Deep-link data (proposalId, eventId, messageId …). */
	data?: Record<string, string>;
	/** Chat messages push but don't clutter the Activity list. */
	record?: boolean;
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

	if (input.record !== false) {
		const rows = userIds.map((userId) => ({
			id: newId(),
			userId,
			groupId: input.groupId,
			kind: input.kind,
			title: input.title,
			body: input.body,
			data,
			createdAt: new Date()
		}));
		await db.insert(notifications).values(rows);
		for (const row of rows) {
			publishToUsers([row.userId], {
				type: 'notification.created',
				groupId: row.groupId,
				payload: toNotification({ ...row, readAt: null })
			});
		}
	}

	// Push is best effort and shouldn't hold up the request.
	void sendPush(userIds, { title: input.title, body: input.body, data });
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

export async function markRead(userId: string, ids?: string[]): Promise<void> {
	const scope = ids?.length ? inArray(notifications.id, ids) : undefined;
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
