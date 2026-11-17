import { describe, expect, test } from 'bun:test';
import type { Message } from '#lib/types';
import { applyMessageUpdate, canModifyMessage, deletedCopy } from './chat-messages';

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
