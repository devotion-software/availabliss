import { expect, test } from 'bun:test';
import type { Proposal, ResponseKind } from '#lib/types';
import { RESPONSE_PILL, acceptedText, confirmedStatus, everyoneIn, knownResponse, maybeCount, myResponse } from './responses';

const proposal = (...replies: ResponseKind[]) =>
	({ responses: replies.map((response, i) => ({ userId: `u${i}`, response, setBy: null, updatedAt: '' })) }) as unknown as Proposal;

test('maybes are counted apart from yeses', () => {
	const p = proposal('accepted', 'accepted', 'maybe', 'declined', 'pending');
	expect(maybeCount(p)).toBe(1);
	expect(acceptedText(p, 5)).toBe('2 of 5 accepted · 1 maybe');
	expect(acceptedText(proposal('accepted', 'pending'), 2)).toBe('1 of 2 accepted');
});

test('a maybe is not everyone being in', () => {
	expect(everyoneIn(proposal('accepted', 'accepted'))).toBe(true);
	expect(everyoneIn(proposal('accepted', 'maybe'))).toBe(false);
	expect(confirmedStatus(proposal('accepted', 'accepted'))).toBe("Everyone's in");
	expect(confirmedStatus(proposal('accepted', 'accepted', 'maybe', 'declined'))).toBe("It's on · 2 of 4 coming · 1 maybe");
	expect(confirmedStatus(proposal('accepted', 'declined'))).toBe("It's on · 1 of 2 coming");
});

test('unknown replies read as waiting', () => {
	expect(knownResponse('maybe')).toBe('maybe');
	expect(knownResponse('tentative')).toBe('pending');
	expect(knownResponse('toString')).toBe('pending');
	expect(knownResponse(undefined)).toBe('pending');
	expect(myResponse(proposal('maybe'), 'u0')).toBe('maybe');
	expect(myResponse(proposal('maybe'), 'nobody')).toBeNull();
	expect(RESPONSE_PILL.maybe.text).toBe('Maybe');
});
