import { expect, test } from 'bun:test';
import type { Member } from '#lib/types';
import { clashShort, clashText } from './clash';

const m = (userId: string, name: string) => [userId, { userId, name } as Member] as const;
const members = new Map([m('me', 'Sam Taylor'), m('p', 'Priya Shah'), m('a', 'Alex'), m('j', 'Jo'), m('t', 'Tom'), m('l', 'Lee')]);

test('clashText', () => {
	expect(clashText([], members, 'me')).toBeNull();
	expect(clashText(['p'], members, 'me')).toBe('Priya is busy then');
	expect(clashText(['me'], members, 'me')).toBe("You're busy then");
	expect(clashText(['me', 'p'], members, 'me')).toBe('You and Priya are busy then');
	expect(clashText(['p', 'a', 'j', 't', 'l'], members, 'me')).toBe('Priya, Alex, Jo and 2 others are busy then');
	expect(clashText(['gone'], members, 'me')).toBe('Someone is busy then');
});

test('clashShort', () => {
	expect(clashShort([], members)).toBeNull();
	expect(clashShort(['p'], members)).toBe('Priya busy then');
	expect(clashShort(['p', 'a'], members)).toBe('2 busy then');
});
