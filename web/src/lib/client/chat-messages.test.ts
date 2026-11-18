import { describe, expect, test } from 'bun:test';
import type { Message } from '#lib/types';
import type { Member } from '#lib/types';
import { applyMessageUpdate, canModifyMessage, canReplyTo, deletedCopy, replyAuthorName, replyPreview, replySnippet, replyingTo, threadReplyTarget, withThreadMessage } from './chat-messages';

const msg = (over: Partial<Message> = {}): Message => ({
	id: 'm1',
	groupId: 'g1',
	authorId: 'me',
	kind: 'text',
	body: 'hello',
	poll: null,
	proposalId: null,
	eventId: null,
	createdAt: '2026-11-16T10:00:00.000Z',
	editedAt: null,
	deleted: false,
	receipt: { delivered: 2, seen: 1, recipients: 3, seenBy: ['u2'] },
	replyTo: null,
	attachments: [],
	caption: null,
	...over
});

describe('canModifyMessage', () => {
	test('your own sent text messages', () => {
		expect(canModifyMessage(msg(), 'me')).toBe(true);
	});
	test('not others’, deleted, pending or non-text messages', () => {
		expect(canModifyMessage(msg({ authorId: 'u2' }), 'me')).toBe(false);
		expect(canModifyMessage(msg({ deleted: true, body: null }), 'me')).toBe(false);
		expect(canModifyMessage(msg({ id: 'temp-1' }), 'me')).toBe(false);
		expect(canModifyMessage(msg({ kind: 'poll' }), 'me')).toBe(false);
		expect(canModifyMessage(msg({ kind: 'system', authorId: null }), 'me')).toBe(false);
	});
});

describe('applyMessageUpdate', () => {
	const list = [msg({ id: 'a' }), msg(), msg({ id: 'b' })];

	test('an edit replaces body and editedAt but keeps the receipt', () => {
		const next = applyMessageUpdate(list, msg({ body: 'hello!', editedAt: '2026-11-16T10:05:00.000Z', receipt: null }));
		expect(next).not.toBe(list);
		expect(next[1].body).toBe('hello!');
		expect(next[1].editedAt).toBe('2026-11-16T10:05:00.000Z');
		expect(next[1].receipt).toEqual(list[1].receipt);
		expect(next[0]).toBe(list[0]);
		expect(next[2]).toBe(list[2]);
	});

	test('a deletion clears the body and flags it, keeping the receipt', () => {
		const next = applyMessageUpdate(list, msg({ body: null, deleted: true, receipt: null }));
		expect(next[1].deleted).toBe(true);
		expect(next[1].body).toBeNull();
		expect(next[1].receipt?.seenBy).toEqual(['u2']);
	});

	test('a newer receipt on the update wins', () => {
		const receipt = { delivered: 3, seen: 3, recipients: 3, seenBy: ['u2', 'u3', 'u4'] };
		expect(applyMessageUpdate(list, msg({ receipt }))[1].receipt).toEqual(receipt);
	});

	test('unknown messages leave the list alone', () => {
		expect(applyMessageUpdate(list, msg({ id: 'zzz' }))).toBe(list);
	});
});

test('deletedCopy', () => {
	const d = deletedCopy(msg());
	expect(d.body).toBeNull();
	expect(d.deleted).toBe(true);
	expect(d.receipt).toEqual(msg().receipt);
});

describe('replies', () => {
	const quote = (over = {}) => ({ id: 'm1', authorId: 'u2', authorName: 'Priya Shah', kind: 'text' as const, text: 'hello', deleted: false, ...over });
	const members = new Map([['u2', { userId: 'u2', name: 'Priya Shah' } as Member]]);

	test('text and polls that are sent and still there can be replied to', () => {
		expect(canReplyTo(msg())).toBe(true);
		expect(canReplyTo(msg({ kind: 'poll' }))).toBe(true);
		expect(canReplyTo(msg({ id: 'temp-1' }))).toBe(false);
		expect(canReplyTo(msg({ deleted: true, body: null }))).toBe(false);
		expect(canReplyTo(msg({ kind: 'system', authorId: null }))).toBe(false);
	});

	test('a quote is one line of the text, or the poll question', () => {
		expect(replyPreview(msg({ body: 'Who is  in?\nMe' })).text).toBe('Who is in? Me');
		const poll = msg({ kind: 'poll', body: null, poll: { id: 'p', question: 'Which park?', multi: false, closed: false, options: [] } });
		expect(replySnippet(replyPreview(poll))).toBe('Poll: Which park?');
		expect(replySnippet(quote({ deleted: true, text: null }))).toBe('Deleted message');
	});

	test('names: you, the member, or the name sent for someone who has left', () => {
		expect(replyAuthorName(quote({ authorId: 'me' }), 'me', members)).toBe('You');
		expect(replyAuthorName(quote(), 'me', members)).toBe('Priya Shah');
		expect(replyAuthorName(quote({ authorId: 'gone', authorName: 'Leo' }), 'me', members)).toBe('Leo');
		expect(replyingTo(msg({ id: 'a', authorId: 'u2' }), 'me', members)).toEqual({ id: 'a', name: 'Priya Shah', snippet: 'hello' });
	});

	test('edits and deletes of the original refresh quotes of it, even when it is not loaded', () => {
		const list = [msg({ id: 'r', replyTo: quote() })];
		const edited = applyMessageUpdate(list, msg({ id: 'm1', authorId: 'u2', body: 'hello again' }));
		expect(edited[0].replyTo).toMatchObject({ text: 'hello again', deleted: false, authorName: 'Priya Shah' });
		const deleted = applyMessageUpdate(edited, msg({ id: 'm1', authorId: 'u2', body: null, deleted: true }));
		expect(deleted[0].replyTo).toMatchObject({ text: null, deleted: true });
	});
});

describe('threads', () => {
	const quote = (id: string) => ({ id, authorId: 'u2', authorName: null, kind: 'text' as const, text: `hi ${id}`, deleted: false });
	const thread = [
		msg({ id: '1', authorId: 'u2' }),
		msg({ id: '2', authorId: 'me', replyTo: quote('1') }),
		msg({ id: '3', authorId: 'u2', replyTo: quote('2') })
	];

	test('replies to the thread join it; others stay out', () => {
		expect(withThreadMessage(thread, msg({ id: '4', authorId: 'u3', replyTo: quote('2') })).map((m) => m.id)).toEqual(['1', '2', '3', '4']);
		expect(withThreadMessage(thread, msg({ id: '5', authorId: 'u3' }))).toBe(thread);
		expect(withThreadMessage(thread, msg({ id: '6', authorId: 'u3', replyTo: quote('99') }))).toBe(thread);
		expect(withThreadMessage(thread, msg({ id: '3', authorId: 'u2', body: null, deleted: true, receipt: null }))[2].deleted).toBe(true);
	});

	test('replies go to the picked message, else the latest from someone else', () => {
		expect(threadReplyTarget(thread, null, 'me')?.id).toBe('3');
		expect(threadReplyTarget(thread, '2', 'me')?.id).toBe('2');
		// Straight after you send, not to yourself.
		expect(threadReplyTarget([...thread, msg({ id: '4', replyTo: quote('3') })], null, 'me')?.id).toBe('3');
		// A picked message deleted since falls back.
		expect(threadReplyTarget([msg({ id: '1', authorId: 'u2' }), msg({ id: '2', authorId: 'u3', deleted: true, body: null })], '2', 'me')?.id).toBe('1');
		expect(threadReplyTarget([msg({ id: '1' })], null, 'me')?.id).toBe('1');
		expect(threadReplyTarget([], null, 'me')).toBeNull();
	});
});

describe('replyPreview with attachments', () => {
	const photo = { id: 'a1', kind: 'image' as const, name: 'p.jpg', mime: 'image/jpeg', size: 1, width: 1, height: 1, url: 'u', thumbnailUrl: null, pass: null };
	test('the caption when there is one', () => {
		expect(replyPreview(msg({ body: 'Look', caption: 'Look', attachments: [photo] })).text).toBe('Look');
	});
	test('what is attached when there is no caption', () => {
		expect(replyPreview(msg({ body: '📷 Photo', caption: null, attachments: [photo] })).text).toBe('Photo');
		expect(replyPreview(msg({ body: '📎 Plan.pdf', attachments: [{ ...photo, kind: 'file', name: 'Plan.pdf' }] })).text).toBe('Plan.pdf');
	});
});
