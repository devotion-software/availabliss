import { describe, expect, test } from 'bun:test';
import type { Message } from '#lib/types';
import { applyReactions, canReact, myReaction, reactorNames, toggled, typedEmoji, withReaction } from './reactions';

const r = (emoji: string, ...userIds: string[]) => ({ emoji, userIds });

describe('withReaction', () => {
	test('adds a new emoji at the end', () => {
		expect(withReaction([r('👍', 'a')], 'me', '❤️')).toEqual([r('👍', 'a'), r('❤️', 'me')]);
	});
	test('joins an emoji someone already used', () => {
		expect(withReaction([r('👍', 'a')], 'me', '👍')).toEqual([r('👍', 'a', 'me')]);
	});
	test('replaces your earlier reaction, dropping an emoji left with no one', () => {
		expect(withReaction([r('😂', 'me'), r('👍', 'a')], 'me', '👍')).toEqual([r('👍', 'a', 'me')]);
	});
	test('null takes yours back', () => {
		expect(withReaction([r('👍', 'a', 'me')], 'me', null)).toEqual([r('👍', 'a')]);
	});
	test("doesn't change the array it's given", () => {
		const before = [r('👍', 'me')];
		withReaction(before, 'me', null);
		expect(before).toEqual([r('👍', 'me')]);
	});
});

test('toggled: tapping your own reaction takes it back', () => {
	expect(toggled([r('❤️', 'me')], 'me', '❤️')).toBeNull();
	expect(toggled([r('❤️', 'me')], 'me', '👍')).toBe('👍');
	expect(myReaction([r('❤️', 'a'), r('👍', 'me')], 'me')).toBe('👍');
	expect(myReaction([], 'me')).toBeNull();
});

test('typedEmoji accepts one emoji, joined sequences and flags included', () => {
	expect(typedEmoji(' 👍🏽 ')).toBe('👍🏽');
	expect(typedEmoji('👨‍👩‍👧')).toBe('👨‍👩‍👧');
	expect(typedEmoji('🇬🇧')).toBe('🇬🇧');
	expect(typedEmoji('❤️')).toBe('❤️');
	expect(typedEmoji('👍👍')).toBeNull();
	expect(typedEmoji('a')).toBeNull();
	expect(typedEmoji('')).toBeNull();
});

test('reactorNames puts you first', () => {
	const name = (id: string) => ({ a: 'Ana Lee', b: 'Sam Ray', c: 'Kim', d: 'Jo' })[id] ?? 'Someone';
	expect(reactorNames(['a'], 'me', name)).toBe('Ana');
	expect(reactorNames(['a', 'me'], 'me', name)).toBe('You and Ana');
	expect(reactorNames(['a', 'b', 'me'], 'me', name)).toBe('You, Ana and Sam');
	expect(reactorNames(['a', 'b', 'c', 'd'], 'me', name)).toBe('Ana, Sam and 2 others');
});

test('applyReactions replaces one message', () => {
	const m = { id: 'x', kind: 'text', deleted: false, reactions: [] } as unknown as Message;
	const list = [m];
	expect(applyReactions(list, { messageId: 'y', reactions: [] })).toBe(list);
	expect(applyReactions(list, { messageId: 'x', reactions: [r('👍', 'a')] })[0].reactions).toEqual([r('👍', 'a')]);
	expect(canReact(m)).toBe(true);
	expect(canReact({ ...m, kind: 'system' })).toBe(false);
	expect(canReact({ ...m, deleted: true })).toBe(false);
});
