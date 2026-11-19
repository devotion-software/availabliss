/** Group chat: messages, polls and per-recipient delivery / read receipts. */
import { and, asc, desc, eq, inArray, isNull, lt, lte, ne, or, sql } from 'drizzle-orm';
import type { Attachment, Message, MessageInfo, MessageKind, MessageLocation, MessageReply, MessageThread, Poll } from '#lib/types';
import { db } from './db';
import { groupMembers, groups, messageReceipts, messages, pollOptions, polls, pollVotes, user } from './db/schema';
import { badRequest, conflict, forbidden, notFound } from './http';
import { alias } from 'drizzle-orm/mysql-core';
import { newId } from './ids';
import { getGroupRow, memberIds, memberName, requireMember } from './groups';
import { slotLabel } from './availability/labels';
import { publishToGroup } from './realtime';
import { markChatRead, notify } from './notifications';
import { displayName } from './names';
import { attachmentsFor, attachmentSummary, claimUploads, deleteMessageAttachments, pendingUploads } from './attachments';

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
				.map((o) => ({
					id: o.id,
					label: o.label,
					voterIds: voteRows.filter((v) => v.optionId === o.id).map((v) => v.userId),
					...(o.start && o.end && { slot: { start: o.start.toISOString(), end: o.end.toISOString(), allDay: o.allDay } })
				}))
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

export async function vote(pollId: string, userId: string, picked: string[]): Promise<Poll> {
	// A repeated id would hit the votes primary key.
	const optionIds = [...new Set(picked)];
	const row = await pollGroup(pollId);
	await requireMember(row.groupId, userId);
	if (row.closedAt) throw conflict('This poll is closed');
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

/** Edits a poll (its author, while it's open); see `updatePollSchema`. */
export async function updatePoll(
	pollId: string,
	userId: string,
	input: { question?: string; multi?: boolean; options?: { id?: string; label: string }[] }
): Promise<Poll> {
	const row = await pollGroup(pollId);
	await requireMember(row.groupId, userId);
	if (row.createdBy !== userId) throw forbidden('Only the person who made the poll can edit it');
	if (row.closedAt) throw conflict('This poll is closed');
	const current = await getPoll(pollId);
	const dated = current.options.some((o) => o.slot);
	if (input.options && dated) throw badRequest("A date poll's times can't be changed");
	if (input.options) {
		const known = new Set(current.options.map((o) => o.id));
		if (input.options.some((o) => o.id && !known.has(o.id))) throw badRequest('Unknown option');
		const labels = input.options.map((o) => o.label.toLowerCase());
		if (new Set(labels).size !== labels.length) throw badRequest('Each option needs to be different');
	}

	await db.transaction(async (tx) => {
		if (input.question !== undefined || input.multi !== undefined) {
			await tx
				.update(polls)
				.set({ ...(input.question !== undefined && { question: input.question }), ...(input.multi !== undefined && { multi: input.multi }) })
				.where(eq(polls.id, pollId));
		}
		if (input.options) {
			const keep = new Set(input.options.flatMap((o) => (o.id ? [o.id] : [])));
			const gone = current.options.filter((o) => !keep.has(o.id)).map((o) => o.id);
			if (gone.length) await tx.delete(pollOptions).where(inArray(pollOptions.id, gone));
			for (const [position, option] of input.options.entries()) {
				if (option.id) await tx.update(pollOptions).set({ label: option.label, position }).where(eq(pollOptions.id, option.id));
				else await tx.insert(pollOptions).values({ id: newId(), pollId, label: option.label, position });
			}
		}
		// Now pick-one: anyone with several picks keeps just their first (in the poll's order).
		if (input.multi === false && current.multi) {
			const order = await tx.select({ id: pollOptions.id, position: pollOptions.position }).from(pollOptions).where(eq(pollOptions.pollId, pollId));
			const position = new Map(order.map((o) => [o.id, o.position]));
			const votes = await tx.select().from(pollVotes).where(eq(pollVotes.pollId, pollId));
			const byUser = Map.groupBy(votes, (v) => v.userId);
			for (const [voter, picks] of byUser) {
				if (picks.length < 2) continue;
				const [, ...rest] = picks.sort((a, b) => (position.get(a.optionId) ?? 0) - (position.get(b.optionId) ?? 0));
				await tx.delete(pollVotes).where(
					and(
						eq(pollVotes.userId, voter),
						inArray(
							pollVotes.optionId,
							rest.map((r) => r.optionId)
						)
					)
				);
			}
		}
	});
	const updated = await getPoll(pollId);
	await publishToGroup(row.groupId, 'poll.updated', updated);
	return updated;
}

export async function closePoll(pollId: string, userId: string): Promise<Poll> {
	const row = await pollGroup(pollId);
	await requireMember(row.groupId, userId);
	if (row.createdBy !== userId) throw forbidden('Only the person who made the poll can close it');
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

/** Receipt summaries published per markReceipts call; clients only show recent messages. */
const PUBLISHED_RECEIPTS = 500;

/**
 * Marks every message up to and including `upTo` (that the user didn't write)
 * as delivered or seen, then tells the group which receipts changed.
 */
export async function markReceipts(groupId: string, userId: string, upTo: string, state: 'delivered' | 'seen') {
	await requireMember(groupId, userId);
	const anchor = await db.query.messages.findFirst({ where: and(eq(messages.id, upTo), eq(messages.groupId, groupId)) });
	if (!anchor) throw notFound('Message not found');
	if (state === 'seen') await markChatRead(userId, groupId);

	// Aliased: the insert below targets message_receipts too, and its ON DUPLICATE
	// KEY UPDATE must only see the target table.
	const own = alias(messageReceipts, 'own');
	const ownReceipt = and(eq(own.messageId, messages.id), eq(own.userId, userId));
	const pending = and(
		eq(messages.groupId, groupId),
		lte(messages.seq, anchor.seq),
		or(isNull(messages.authorId), ne(messages.authorId, userId)),
		state === 'seen' ? isNull(own.seenAt) : isNull(own.deliveredAt)
	);
	// The newest ones are what the group gets told about.
	const recent = await db
		.select({ id: messages.id })
		.from(messages)
		.leftJoin(own, ownReceipt)
		.where(pending)
		.orderBy(desc(messages.seq))
		.limit(PUBLISHED_RECEIPTS);
	if (!recent.length) return;

	// Every pending message is marked in one statement, however far back it goes.
	const now = new Date();
	await db
		.insert(messageReceipts)
		.select(
			db
				.select({
					messageId: messages.id,
					userId: sql<string>`${userId}`.as('user_id'),
					deliveredAt: sql<Date>`${now}`.as('delivered_at'),
					seenAt: sql<Date | null>`${state === 'seen' ? now : null}`.as('seen_at')
				})
				.from(messages)
				.leftJoin(own, ownReceipt)
				.where(pending)
		)
		.onDuplicateKeyUpdate({
			set: state === 'seen' ? { seenAt: sql`coalesce(${messageReceipts.seenAt}, values(${messageReceipts.seenAt}))` } : { userId }
		});

	const changed = await db
		.select()
		.from(messages)
		.where(
			inArray(
				messages.id,
				recent.map((p) => p.id)
			)
		);
	const summaries = await receiptSummaries(changed, (await memberIds(groupId)).length);
	await publishToGroup(groupId, 'message.receipts', { userId, state, receipts: [...summaries.values()] });
}

export async function messageInfo(messageId: string, userId: string): Promise<MessageInfo> {
	const message = await db.query.messages.findFirst({ where: eq(messages.id, messageId) });
	if (!message) throw notFound('Message not found');
	// Any member can see who has read a message, not only its sender.
	await requireMember(message.groupId, userId);

	const [receipts, members] = await Promise.all([
		db.select().from(messageReceipts).where(eq(messageReceipts.messageId, messageId)),
		memberIds(message.groupId)
	]);
	const byUser = new Map(receipts.map((r) => [r.userId, r]));
	const others = members.filter((id) => id !== message.authorId);
	return {
		messageId,
		sentAt: message.createdAt.toISOString(),
		seen: receipts.filter((r) => r.seenAt).map((r) => ({ userId: r.userId, at: r.seenAt!.toISOString() })),
		delivered: receipts.filter((r) => !r.seenAt).map((r) => ({ userId: r.userId, at: r.deliveredAt.toISOString() })),
		pending: others.filter((id) => !byUser.has(id))
	};
}

// ─── Locations ────────────────────────────────────────────────────────────

/** Coordinates as sent in links: 6 decimal places is about 10 cm. */
const coord = (n: number) => String(Number(n.toFixed(6)));

/** One line for reply quotes and notifications: "📍 Dishoom Shoreditch" or "📍 Location". */
export const locationSummary = (location: Pick<MessageLocation, 'name'>) => `📍 ${location.name || 'Location'}`;

/** What apps that don't know `location` show as the text: the summary, the address and a maps link. */
export function locationFallback(location: MessageLocation): string {
	const query = `${coord(location.lat)},${coord(location.lng)}`;
	return [locationSummary(location), location.address, `https://www.google.com/maps/search/?api=1&query=${query}`].filter(Boolean).join('\n');
}

// ─── Replies ──────────────────────────────────────────────────────────────

/** Quoted text is one line in the clients; a little more than fits is plenty. */
const REPLY_SNIPPET_CHARS = 140;
export const replySnippet = (text: string) => {
	const line = text.replace(/\s+/g, ' ').trim();
	return [...line].length > REPLY_SNIPPET_CHARS
		? `${[...line]
				.slice(0, REPLY_SNIPPET_CHARS - 1)
				.join('')
				.trimEnd()}…`
		: line;
};

/** Previews of the messages being replied to, as they are now (edits and deletion included). */
async function loadReplies(ids: string[]): Promise<Map<string, MessageReply>> {
	const result = new Map<string, MessageReply>();
	if (!ids.length) return result;
	const rows = await db
		.select({
			id: messages.id,
			authorId: messages.authorId,
			kind: messages.kind,
			body: messages.body,
			deletedAt: messages.deletedAt,
			location: messages.location,
			question: polls.question,
			name: user.name,
			email: user.email,
			nameInGroup: groupMembers.displayName
		})
		.from(messages)
		.leftJoin(polls, eq(polls.id, messages.pollId))
		.leftJoin(user, eq(user.id, messages.authorId))
		.leftJoin(groupMembers, and(eq(groupMembers.userId, messages.authorId), eq(groupMembers.groupId, messages.groupId)))
		.where(inArray(messages.id, [...new Set(ids)]));
	// Photos and files sent without a caption are quoted as "📷 Photo", "📎 Plan.pdf" and so on.
	const attachments = await attachmentsFor(rows.filter((r) => !r.deletedAt && r.kind === 'text' && !r.body).map((r) => r.id));
	for (const r of rows) {
		const deleted = r.deletedAt !== null;
		const text = deleted
			? null
			: r.kind === 'poll'
				? r.question
				: r.location
					? locationSummary(r.location)
					: r.body || attachmentSummary(attachments.get(r.id) ?? []);
		result.set(r.id, {
			id: r.id,
			authorId: r.authorId,
			authorName: r.authorId ? displayName(r, 'Someone') : null,
			kind: r.kind,
			text: text ? replySnippet(text) : null,
			deleted
		});
	}
	return result;
}

// ─── Messages ─────────────────────────────────────────────────────────────

async function toMessages(rows: MessageRow[], viewerId: string, groupSize: number): Promise<Message[]> {
	const [pollMap, replies, attachments] = await Promise.all([
		loadPolls(rows.flatMap((r) => (r.pollId ? [r.pollId] : []))),
		loadReplies(rows.flatMap((r) => (r.replyToId ? [r.replyToId] : []))),
		attachmentsFor(rows.filter((r) => !r.deletedAt && r.kind === 'text').map((r) => r.id))
	]);
	const own = rows.filter((r) => r.authorId === viewerId);
	const receipts = await receiptSummaries(own, groupSize);
	return rows.map((r) => {
		const receipt = receipts.get(r.id);
		const files = attachments.get(r.id) ?? [];
		const location = r.deletedAt ? null : r.location;
		return {
			id: r.id,
			groupId: r.groupId,
			authorId: r.authorId,
			kind: r.kind,
			// Older apps only know `body`: without a caption they get a summary of the attachments.
			// …and for a location, a summary with a maps link.
			body: r.deletedAt ? null : location ? locationFallback(location) : files.length ? r.body || attachmentSummary(files) : r.body,
			poll: r.pollId ? (pollMap.get(r.pollId) ?? null) : null,
			proposalId: r.proposalId,
			eventId: r.eventId,
			createdAt: r.createdAt.toISOString(),
			editedAt: r.editedAt?.toISOString() ?? null,
			deleted: r.deletedAt !== null,
			receipt: receipt ? { delivered: receipt.delivered, seen: receipt.seen, recipients: receipt.recipients, seenBy: receipt.seenBy } : null,
			replyTo: r.replyToId ? (replies.get(r.replyToId) ?? null) : null,
			attachments: files,
			caption: files.length ? r.body || null : null,
			location
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

/** Most messages a thread returns (the root and the earliest replies). */
export const THREAD_LIMIT = 200;
/** How far up a chain of replies the root is looked for. */
const THREAD_MAX_DEPTH = 100;

/**
 * The thread a message is part of: the first message of its chain of replies (walking
 * `replyTo` up), then every reply to it and replies to those, oldest first.
 */
export async function messageThread(messageId: string, userId: string): Promise<MessageThread> {
	const start = await db.query.messages.findFirst({ where: eq(messages.id, messageId) });
	if (!start) throw notFound('Message not found');
	await requireMember(start.groupId, userId);
	const groupId = start.groupId;

	let root = start;
	const climbed = new Set([root.id]);
	while (root.replyToId && climbed.size < THREAD_MAX_DEPTH) {
		const parent = await db.query.messages.findFirst({ where: and(eq(messages.id, root.replyToId), eq(messages.groupId, groupId)) });
		if (!parent || climbed.has(parent.id)) break;
		climbed.add(parent.id);
		root = parent;
	}

	// Breadth first, a level of replies at a time, until the cap.
	const rows: MessageRow[] = [root];
	const seen = new Set([root.id]);
	let frontier = [root.id];
	let truncated = false;
	while (frontier.length && !truncated) {
		const level = await db
			.select()
			.from(messages)
			.where(and(eq(messages.groupId, groupId), inArray(messages.replyToId, frontier)))
			.orderBy(asc(messages.seq))
			.limit(THREAD_LIMIT + 1);
		frontier = [];
		for (const row of level) {
			if (seen.has(row.id)) continue;
			if (rows.length >= THREAD_LIMIT) {
				truncated = true;
				break;
			}
			seen.add(row.id);
			rows.push(row);
			frontier.push(row.id);
		}
	}
	rows.sort((a, b) => a.seq - b.seq);
	return { rootId: root.id, messages: await toMessages(rows, userId, (await memberIds(groupId)).length), truncated };
}

interface PostInput {
	kind: MessageKind;
	body?: string | null;
	pollId?: string | null;
	proposalId?: string | null;
	eventId?: string | null;
	replyToId?: string | null;
	location?: MessageLocation | null;
	/** Checked unsent uploads (see pendingUploads), attached in this order. */
	attachments?: { id: string; kind: Attachment['kind']; name: string; pass?: Attachment['pass'] }[];
}

/** Messages can be 4000 characters, but APNs and FCM reject payloads over 4 KB. */
const PUSH_PREVIEW_CHARS = 200;
export const pushPreview = (text: string) =>
	text.length > PUSH_PREVIEW_CHARS
		? `${[...text]
				.slice(0, PUSH_PREVIEW_CHARS - 1)
				.join('')
				.trimEnd()}…`
		: text;

/** Inserts a message, publishes it live and pushes it to everyone else. */
export async function postMessage(groupId: string, authorId: string | null, input: PostInput): Promise<Message> {
	const id = newId();
	const { attachments: uploads = [], ...values } = input;
	await db.insert(messages).values({ id, groupId, authorId, ...values });
	if (uploads.length)
		await claimUploads(
			id,
			uploads.map((a) => a.id)
		);
	const row = (await db.query.messages.findFirst({ where: eq(messages.id, id) }))!;
	const members = await memberIds(groupId);
	const [message] = await toMessages([row], authorId ?? '', members.length);
	await publishToGroup(groupId, 'message.created', message);

	if (authorId && (input.kind === 'text' || input.kind === 'poll')) {
		const [author, group] = await Promise.all([
			memberName(groupId, authorId),
			db.query.groups.findFirst({ where: eq(groups.id, groupId), columns: { name: true } })
		]);
		const poll = message.poll ? `Poll: ${message.poll.question}` : null;
		// One Activity row per group's chat, kept to its latest message ("Sam: See you there").
		await notify(
			members.filter((m) => m !== authorId),
			{
				kind: 'message',
				groupId,
				title: group?.name ?? 'New message',
				body: pushPreview(`${author}: ${poll ?? (input.location ? locationSummary(input.location) : input.body || attachmentSummary(uploads))}`),
				data: { messageId: id },
				record: 'per-group'
			}
		);
	}
	return message;
}

/** A text message: `body`, or attachments (uploaded first) with an optional caption, or both. */
export async function sendText(
	groupId: string,
	userId: string,
	body: string | null,
	replyToId: string | null = null,
	attachmentIds: string[] = []
): Promise<Message> {
	await requireMember(groupId, userId);
	if (!body && !attachmentIds.length) throw badRequest('Write a message or attach something');
	const attachments = await pendingUploads(groupId, userId, attachmentIds);
	await checkReplyTo(groupId, replyToId);
	return postMessage(groupId, userId, { kind: 'text', body: body || null, replyToId, attachments });
}

/** A place: the sender's current location or one they searched for (a text message with `location`). */
export async function sendLocation(groupId: string, userId: string, location: MessageLocation, replyToId: string | null = null): Promise<Message> {
	await requireMember(groupId, userId);
	await checkReplyTo(groupId, replyToId);
	return postMessage(groupId, userId, { kind: 'text', location, replyToId });
}

async function checkReplyTo(groupId: string, replyToId: string | null) {
	if (!replyToId) return;
	const original = await db.query.messages.findFirst({
		where: and(eq(messages.id, replyToId), eq(messages.groupId, groupId)),
		columns: { kind: true, deletedAt: true }
	});
	if (!original) throw badRequest('The message being replied to is not in this chat');
	if (original.kind === 'system') throw badRequest("Group updates can't be replied to");
	if (original.deletedAt) throw conflict('That message was deleted');
}

/** The author's own text message, for editing or deleting. */
async function ownText(messageId: string, userId: string) {
	const row = await db.query.messages.findFirst({ where: eq(messages.id, messageId) });
	if (!row) throw notFound('Message not found');
	await requireMember(row.groupId, userId);
	if (row.authorId !== userId) throw forbidden('Only the person who sent a message can change it');
	if (row.kind !== 'text') throw badRequest('Only text messages can be edited or deleted');
	if (row.deletedAt) throw conflict('That message was deleted');
	return row;
}

/** Republishes a changed message to the group (`message.updated`), as the author sees it. */
async function publishUpdated(messageId: string, userId: string): Promise<Message> {
	const row = (await db.query.messages.findFirst({ where: eq(messages.id, messageId) }))!;
	const [message] = await toMessages([row], userId, (await memberIds(row.groupId)).length);
	// Receipts are per viewer: everyone keeps their own.
	await publishToGroup(row.groupId, 'message.updated', { ...message, receipt: null });
	return message;
}

export async function editMessage(messageId: string, userId: string, body: string): Promise<Message> {
	const row = await ownText(messageId, userId);
	if (row.location) throw badRequest("A location can't be edited");
	const files = (await attachmentsFor([messageId])).get(messageId) ?? [];
	if (!body && !files.length) throw badRequest('Write a message');
	// An older app editing a photo shows its summary as the text; sent back unchanged, it isn't a caption.
	const caption = files.length && !row.body && body === attachmentSummary(files) ? null : body || null;
	if (row.body !== caption) await db.update(messages).set({ body: caption, editedAt: new Date() }).where(eq(messages.id, messageId));
	return publishUpdated(messageId, userId);
}

export async function deleteMessage(messageId: string, userId: string): Promise<void> {
	await ownText(messageId, userId);
	await db.update(messages).set({ body: null, deletedAt: new Date() }).where(eq(messages.id, messageId));
	await deleteMessageAttachments(messageId);
	await publishUpdated(messageId, userId);
}

export type PollInput =
	| { question: string; options: string[]; multi: boolean }
	/** A date poll: a shortlist of times from the group calendar, labelled in the group's time zone. */
	| { question: string; slots: { start: string; end: string; allDay: boolean }[]; multi: boolean };

export async function sendPoll(groupId: string, userId: string, input: PollInput): Promise<Message> {
	await requireMember(groupId, userId);
	const pollId = newId();
	let options: (typeof pollOptions.$inferInsert)[];
	if ('slots' in input) {
		const group = await getGroupRow(groupId);
		const sorted = [...input.slots].sort((a, b) => a.start.localeCompare(b.start));
		options = sorted.map((s, position) => ({
			id: newId(),
			pollId,
			position,
			label: slotLabel({ start: Date.parse(s.start), end: Date.parse(s.end) }, group.requirement).slice(0, 100),
			start: new Date(s.start),
			end: new Date(s.end),
			allDay: s.allDay
		}));
	} else {
		options = input.options.map((label, position) => ({ id: newId(), pollId, label, position }));
	}
	await db.transaction(async (tx) => {
		await tx.insert(polls).values({ id: pollId, groupId, createdBy: userId, question: input.question, multi: input.multi });
		await tx.insert(pollOptions).values(options);
	});
	return postMessage(groupId, userId, { kind: 'poll', pollId });
}
