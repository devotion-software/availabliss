/** The chats list (Activity's Chats tab): every group chat you're in, latest message first. */
import { inArray, max } from 'drizzle-orm';
import type { ChatSummary } from '#lib/types';
import { db } from './db';
import { messages } from './db/schema';
import { groupBase, groupsFor } from './groups';
import { loadReplies } from './chat';
import { unreadByGroup } from './group-summaries';

/**
 * Each group's chat with its latest message (as a one-line preview) and how many messages from others
 * you haven't seen. Newest first; chats with no messages yet come last (newest group first). Archived
 * groups are left out unless something in them is unread.
 */
export async function listChats(userId: string): Promise<ChatSummary[]> {
	const rows = await groupsFor(userId);
	if (!rows.length) return [];
	const ids = rows.map((r) => r.row.id);
	const [latest, unread] = await Promise.all([
		db
			.select({ groupId: messages.groupId, seq: max(messages.seq) })
			.from(messages)
			.where(inArray(messages.groupId, ids))
			.groupBy(messages.groupId),
		unreadByGroup(ids, userId)
	]);
	const seqs = latest.flatMap((l) => (l.seq !== null ? [l.seq] : []));
	const lastRows = seqs.length
		? await db.select({ id: messages.id, groupId: messages.groupId, createdAt: messages.createdAt }).from(messages).where(inArray(messages.seq, seqs))
		: [];
	const lastByGroup = new Map(lastRows.map((r) => [r.groupId, r]));
	const previews = await loadReplies(lastRows.map((r) => r.id));
	const chats: ChatSummary[] = rows.flatMap(({ row, role }) => {
		const unreadCount = unread.get(row.id) ?? 0;
		if (row.archivedAt && !unreadCount) return [];
		const last = lastByGroup.get(row.id);
		const preview = last ? previews.get(last.id) : undefined;
		const { id, name, color, monogram, image, archived } = groupBase(row, role, []);
		return [
			{
				group: { id, name, color, monogram, image, archived },
				lastMessage: last && preview ? { ...preview, createdAt: last.createdAt.toISOString() } : null,
				unreadCount,
				createdAt: row.createdAt.toISOString()
			}
		];
	});
	const when = (c: ChatSummary) => c.lastMessage?.createdAt ?? '';
	return chats.sort((a, b) => when(b).localeCompare(when(a)) || b.createdAt.localeCompare(a.createdAt));
}
