/** Group chat: messages, polls and per-recipient delivery / read receipts. */
import { and, asc, desc, eq, inArray, isNull, lt, lte, ne, or, sql } from 'drizzle-orm';
import type { Message, MessageInfo, MessageKind, Poll } from '#lib/types';
import { db } from './db';
import { messageReceipts, messages, pollOptions, polls, pollVotes, user } from './db/schema';
import { badRequest, forbidden, notFound } from './http';
import { newId } from './ids';
import { memberIds, requireMember } from './groups';
import { publishToGroup } from './realtime';
import { notify } from './notifications';

type MessageRow = typeof messages.$inferSelect;

export interface ReceiptSummary {
	messageId: string;
	delivered: number;
	seen: number;
	recipients: number;
	seenBy: string[];
}

// ─── Polls ────────────────────────────────────────────────────────────────

async function loadPolls(pollIds: string[]): Promise<Map<string, Poll>> {
	const result = new Map<string, Poll>();
	if (!pollIds.length) return result;
	const [pollRows, optionRows, voteRows] = await Promise.all([
		db.select().from(polls).where(inArray(polls.id, pollIds)),
		db.select().from(pollOptions).where(inArray(pollOptions.pollId, pollIds)).orderBy(asc(pollOptions.position)),
		db.select().from(pollVotes).where(inArray(pollVotes.pollId, pollIds))
	]);
	for (const p of pollRows) {
		result.set(p.id, {
			id: p.id,
			question: p.question,
			multi: p.multi,
			closed: p.closedAt !== null,
			options: optionRows
				.filter((o) => o.pollId === p.id)
				.map((o) => ({ id: o.id, label: o.label, voterIds: voteRows.filter((v) => v.optionId === o.id).map((v) => v.userId) }))
		});
	}
	return result;
}

export async function getPoll(pollId: string): Promise<Poll> {
	const poll = (await loadPolls([pollId])).get(pollId);
	if (!poll) throw notFound('Poll not found');
	return poll;
}

async function pollGroup(pollId: string) {
	const row = await db.query.polls.findFirst({ where: eq(polls.id, pollId) });
	if (!row) throw notFound('Poll not found');
	return row;
}

export async function vote(pollId: string, userId: string, optionIds: string[]): Promise<Poll> {
	const row = await pollGroup(pollId);
	await requireMember(row.groupId, userId);
	if (row.closedAt) throw badRequest('This poll is closed');
	if (!row.multi && optionIds.length > 1) throw badRequest('Pick one option');
	const poll = await getPoll(pollId);
	if (!optionIds.every((id) => poll.options.some((o) => o.id === id))) throw badRequest('Unknown option');

	await db.transaction(async (tx) => {
		await tx.delete(pollVotes).where(and(eq(pollVotes.pollId, pollId), eq(pollVotes.userId, userId)));
		if (optionIds.length) await tx.insert(pollVotes).values(optionIds.map((optionId) => ({ optionId, pollId, userId })));
	});
	const updated = await getPoll(pollId);
	await publishToGroup(row.groupId, 'poll.updated', updated);
	return updated;
}

export async function closePoll(pollId: string, userId: string): Promise<Poll> {
	const row = await pollGroup(pollId);
	const role = await requireMember(row.groupId, userId);
	if (row.createdBy !== userId && role === 'member') throw forbidden('Only the poll author or an admin can close it');
	await db.update(polls).set({ closedAt: new Date() }).where(eq(polls.id, pollId));
	const updated = await getPoll(pollId);
	await publishToGroup(row.groupId, 'poll.updated', updated);
	return updated;
}

// ─── Receipts ─────────────────────────────────────────────────────────────

async function receiptSummaries(rows: MessageRow[], groupSize: number): Promise<Map<string, ReceiptSummary>> {
	const result = new Map<string, ReceiptSummary>();
	const ids = rows.map((r) => r.id);
	if (!ids.length) return result;
	const receipts = await db
		.select({ messageId: messageReceipts.messageId, userId: messageReceipts.userId, seenAt: messageReceipts.seenAt })
		.from(messageReceipts)
		.where(inArray(messageReceipts.messageId, ids))
		.orderBy(asc(messageReceipts.seenAt));
	for (const id of ids) {
		const forMessage = receipts.filter((r) => r.messageId === id);
		const seenBy = forMessage.filter((r) => r.seenAt).map((r) => r.userId);
		result.set(id, {
			messageId: id,
			delivered: forMessage.length,
			seen: seenBy.length,
			recipients: Math.max(groupSize - 1, 0),
			seenBy
		});
	}
	return result;
}

/**
 * Marks every message up to and including `upTo` (that the user didn't write)
 * as delivered or seen, then tells the group which receipts changed.
 */
export async function markReceipts(groupId: string, userId: string, upTo: string, state: 'delivered' | 'seen') {
	await requireMember(groupId, userId);
	const anchor = await db.query.messages.findFirst({ where: and(eq(messages.id, upTo), eq(messages.groupId, groupId)) });
	if (!anchor) throw notFound('Message not found');

	const pending = await db
		.select({ id: messages.id, seenAt: messageReceipts.seenAt, deliveredAt: messageReceipts.deliveredAt })
		.from(messages)
		.leftJoin(messageReceipts, and(eq(messageReceipts.messageId, messages.id), eq(messageReceipts.userId, userId)))
		.where(
			and(
				eq(messages.groupId, groupId),
				lte(messages.seq, anchor.seq),
				or(isNull(messages.authorId), ne(messages.authorId, userId)),
				state === 'seen' ? isNull(messageReceipts.seenAt) : isNull(messageReceipts.deliveredAt)
			)
		)
		.orderBy(desc(messages.seq))
		.limit(500);
	if (!pending.length) return;

	const now = new Date();
	await db
		.insert(messageReceipts)
		.values(pending.map((p) => ({ messageId: p.id, userId, deliveredAt: now, seenAt: state === 'seen' ? now : null })))
		.onDuplicateKeyUpdate({
			set: state === 'seen' ? { seenAt: sql`coalesce(${messageReceipts.seenAt}, values(${messageReceipts.seenAt}))` } : { userId }
		});

	const changed = await db.select().from(messages).where(inArray(messages.id, pending.map((p) => p.id)));
	const summaries = await receiptSummaries(changed, (await memberIds(groupId)).length);
	await publishToGroup(groupId, 'message.receipts', { userId, state, receipts: [...summaries.values()] });
}

export async function messageInfo(messageId: string, userId: string): Promise<MessageInfo> {
	const message = await db.query.messages.findFirst({ where: eq(messages.id, messageId) });
	if (!message) throw notFound('Message not found');
	await requireMember(message.groupId, userId);
	if (message.authorId !== userId) throw forbidden('Only the sender can see message info');

	const [receipts, members] = await Promise.all([
		db.select().from(messageReceipts).where(eq(messageReceipts.messageId, messageId)),
		memberIds(message.groupId)
	]);
	const byUser = new Map(receipts.map((r) => [r.userId, r]));
	const others = members.filter((id) => id !== userId);
	return {
		messageId,
		sentAt: message.createdAt.toISOString(),
		seen: receipts.filter((r) => r.seenAt).map((r) => ({ userId: r.userId, at: r.seenAt!.toISOString() })),
		delivered: receipts.filter((r) => !r.seenAt).map((r) => ({ userId: r.userId, at: r.deliveredAt.toISOString() })),
		pending: others.filter((id) => !byUser.has(id))
	};
}

// ─── Messages ─────────────────────────────────────────────────────────────

async function toMessages(rows: MessageRow[], viewerId: string, groupSize: number): Promise<Message[]> {
	const pollMap = await loadPolls(rows.flatMap((r) => (r.pollId ? [r.pollId] : [])));
	const own = rows.filter((r) => r.authorId === viewerId);
	const receipts = await receiptSummaries(own, groupSize);
	return rows.map((r) => {
		const receipt = receipts.get(r.id);
		return {
			id: r.id,
			groupId: r.groupId,
			authorId: r.authorId,
			kind: r.kind,
			body: r.body,
			poll: r.pollId ? (pollMap.get(r.pollId) ?? null) : null,
			proposalId: r.proposalId,
			eventId: r.eventId,
			createdAt: r.createdAt.toISOString(),
			receipt: receipt ? { delivered: receipt.delivered, seen: receipt.seen, recipients: receipt.recipients, seenBy: receipt.seenBy } : null
		};
	});
}

export async function listMessages(groupId: string, userId: string, before: string | null, limit: number) {
	await requireMember(groupId, userId);
	let beforeSeq: number | null = null;
	if (before) {
		const anchor = await db.query.messages.findFirst({ where: and(eq(messages.id, before), eq(messages.groupId, groupId)) });
		if (!anchor) throw notFound('Message not found');
		beforeSeq = anchor.seq;
	}
	const rows = await db
		.select()
		.from(messages)
		.where(and(eq(messages.groupId, groupId), beforeSeq !== null ? lt(messages.seq, beforeSeq) : undefined))
		.orderBy(desc(messages.seq))
		.limit(limit + 1);
	const hasMore = rows.length > limit;
	const page = rows.slice(0, limit).reverse();
	return { messages: await toMessages(page, userId, (await memberIds(groupId)).length), hasMore };
}

interface PostInput {
	kind: MessageKind;
	body?: string | null;
	pollId?: string | null;
	proposalId?: string | null;
	eventId?: string | null;
}

/** Inserts a message, publishes it live and pushes it to everyone else. */
export async function postMessage(groupId: string, authorId: string | null, input: PostInput): Promise<Message> {
	const id = newId();
	await db.insert(messages).values({ id, groupId, authorId, ...input });
	const row = (await db.query.messages.findFirst({ where: eq(messages.id, id) }))!;
	const members = await memberIds(groupId);
	const [message] = await toMessages([row], authorId ?? '', members.length);
	await publishToGroup(groupId, 'message.created', message);

	if (authorId && (input.kind === 'text' || input.kind === 'poll')) {
		const author = await db.query.user.findFirst({ where: eq(user.id, authorId), columns: { name: true } });
		const poll = message.poll ? `Poll: ${message.poll.question}` : null;
		await notify(
			members.filter((m) => m !== authorId),
			{
				kind: 'message',
				groupId,
				title: author?.name ?? 'New message',
				body: poll ?? input.body ?? '',
				data: { messageId: id },
				record: false
			}
		);
	}
	return message;
}

export async function sendText(groupId: string, userId: string, body: string): Promise<Message> {
	await requireMember(groupId, userId);
	return postMessage(groupId, userId, { kind: 'text', body });
}

export async function sendPoll(
	groupId: string,
	userId: string,
	input: { question: string; options: string[]; multi: boolean }
): Promise<Message> {
	await requireMember(groupId, userId);
	const pollId = newId();
	await db.transaction(async (tx) => {
		await tx.insert(polls).values({ id: pollId, groupId, createdBy: userId, question: input.question, multi: input.multi });
		await tx.insert(pollOptions).values(input.options.map((label, position) => ({ id: newId(), pollId, label, position })));
	});
	return postMessage(groupId, userId, { kind: 'poll', pollId });
}

/** Messages in a group the user hasn't seen yet. */
export async function unreadCount(groupId: string, userId: string): Promise<number> {
	const [row] = await db
		.select({ count: sql<number>`count(*)` })
		.from(messages)
		.leftJoin(messageReceipts, and(eq(messageReceipts.messageId, messages.id), eq(messageReceipts.userId, userId)))
		.where(
			and(
				eq(messages.groupId, groupId),
				or(isNull(messages.authorId), ne(messages.authorId, userId)),
				isNull(messageReceipts.seenAt)
			)
		);
	return Number(row?.count ?? 0);
}
