/** Pure helpers for chat messages: who can edit/delete what, and applying edits and deletions. */
import type { Message } from '#lib/types';

export const isTempMessage = (m: Message) => m.id.startsWith('temp-');

/** Only your own, sent, non-deleted text messages can be edited or deleted. */
export const canModifyMessage = (m: Message, meId: string) =>
	m.kind === 'text' && m.authorId === meId && !m.deleted && !isTempMessage(m);

/**
 * Replaces a message by id with an edited or deleted copy (`message.updated` or a PATCH/DELETE result),
 * keeping the receipt we already have: updates carry `receipt: null`.
 * Returns the same array when the message isn't loaded.
 */
export function applyMessageUpdate(messages: Message[], updated: Message): Message[] {
	const i = messages.findIndex((m) => m.id === updated.id);
	if (i < 0) return messages;
	const next = messages.slice();
	next[i] = { ...messages[i], ...updated, receipt: updated.receipt ?? messages[i].receipt };
	return next;
}

/** A message as it reads once deleted: no text, flagged deleted. */
export const deletedCopy = (m: Message): Message => ({ ...m, body: null, deleted: true });
