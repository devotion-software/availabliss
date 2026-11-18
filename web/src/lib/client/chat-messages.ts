/** Pure helpers for chat messages: who can edit/delete what, and applying edits and deletions. */
import type { Member, Message, MessageReply } from '#lib/types';
import { attachmentSummary } from './attachments';
import { locationSummary } from './location';

export const isTempMessage = (m: Message) => m.id.startsWith('temp-');

/** Only your own, sent, non-deleted text messages can be edited or deleted. */
export const canModifyMessage = (m: Message, meId: string) =>
	m.kind === 'text' && m.authorId === meId && !m.deleted && !isTempMessage(m);

/** …and of those, all but shared places can be edited (they can only be deleted). */
export const canEditMessage = (m: Message, meId: string) => canModifyMessage(m, meId) && !m.location;

/**
 * Replaces a message by id with an edited or deleted copy (`message.updated` or a PATCH/DELETE result),
 * keeping the receipt we already have: updates carry `receipt: null`.
 * Returns the same array when the message isn't loaded.
 */
export function applyMessageUpdate(messages: Message[], updated: Message): Message[] {
	const i = messages.findIndex((m) => m.id === updated.id);
	const quoted = messages.some((m) => m.replyTo?.id === updated.id);
	if (i < 0 && !quoted) return messages;
	const next = quoted ? withQuotesOf(messages, updated) : messages.slice();
	if (i >= 0) next[i] = { ...messages[i], ...updated, receipt: updated.receipt ?? messages[i].receipt };
	return next;
}

/** Can be replied to: sent text and polls that are still there. */
export const canReplyTo = (m: Message) => (m.kind === 'text' || m.kind === 'poll') && !!m.authorId && !m.deleted && !isTempMessage(m);

/** A message as a quote: for "Replying to …", an optimistic reply, and refreshing quotes after it changes. */
export function replyPreview(m: Message, authorName: string | null = null): MessageReply {
	// With attachments: the caption, else what they are ("Photo", "GIF", "Plan.pdf").
	const raw =
		m.kind === 'poll'
			? m.poll?.question
			: m.location
				? locationSummary(m.location)
				: m.attachments?.length
					? m.caption || attachmentSummary(m.attachments)
					: m.body;
	return {
		id: m.id,
		authorId: m.authorId,
		authorName,
		kind: m.kind,
		text: m.deleted ? null : (raw?.replace(/\s+/g, ' ').trim() ?? null),
		deleted: m.deleted
	};
}

/** One line for a quote: "Deleted message", "Poll: Which park?" or the text. */
export function replySnippet(r: MessageReply): string {
	if (r.deleted) return 'Deleted message';
	if (r.kind === 'poll') return `Poll: ${r.text ?? ''}`;
	if (r.kind === 'proposal') return r.text ?? 'Proposal';
	if (r.kind === 'event') return r.text ?? 'Event';
	return r.text ?? '';
}

/** Who a quote is from: "You", the member's name, or the name the server sent (someone who has left). */
export const replyAuthorName = (r: { authorId: string | null; authorName?: string | null }, meId: string, members: Map<string, Member>) =>
	r.authorId && r.authorId === meId ? 'You' : ((r.authorId ? members.get(r.authorId)?.name : undefined) ?? r.authorName ?? 'Someone');

/** "Replying to Priya" above the composer: who and one line of what they said. */
export interface ReplyingTo {
	id: string;
	name: string;
	snippet: string;
}

export const replyingTo = (m: Message | null, meId: string, members: Map<string, Member>): ReplyingTo | null =>
	m ? { id: m.id, name: replyAuthorName(m, meId, members), snippet: replySnippet(replyPreview(m)) } : null;

/** Quotes aren't republished when the original is edited or deleted: refreshes loaded replies to it. */
function withQuotesOf(messages: Message[], original: Message): Message[] {
	const fresh = replyPreview(original);
	return messages.map((m) => (m.replyTo?.id === original.id ? { ...m, replyTo: { ...m.replyTo, text: fresh.text, deleted: fresh.deleted } } : m));
}

/**
 * A message arriving while a thread is open: replaces its copy if it's already in the thread,
 * joins the end if it replies to something in it, and is otherwise left out (same array back).
 */
export function withThreadMessage(thread: Message[], message: Message): Message[] {
	const i = thread.findIndex((m) => m.id === message.id);
	if (i >= 0) return applyMessageUpdate(thread, message);
	const parent = message.replyTo?.id;
	return parent && thread.some((m) => m.id === parent) ? [...thread, message] : thread;
}

/**
 * Who a reply in a thread goes to: the message you picked (while it can still be replied to),
 * else the latest one from someone else (so it isn't to yourself straight after you send), else the latest.
 */
export function threadReplyTarget(thread: Message[], pickedId: string | null, meId: string): Message | null {
	const picked = pickedId ? thread.find((m) => m.id === pickedId && canReplyTo(m)) : undefined;
	return picked ?? thread.findLast((m) => canReplyTo(m) && m.authorId !== meId) ?? thread.findLast(canReplyTo) ?? null;
}

/** A message as it reads once deleted: no text, flagged deleted. */
export const deletedCopy = (m: Message): Message => ({ ...m, body: null, deleted: true, location: null });
