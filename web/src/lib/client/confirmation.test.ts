import { describe, expect, test } from 'bun:test';
import type { Message } from '#lib/types';
import { BURST_WINDOW_MS, claimBurst, isConfirmationMessage, isRecentConfirmation } from './confirmation';

const NOW = Date.parse('2026-11-16T12:00:00.000Z');
const ago = (ms: number) => new Date(NOW - ms).toISOString();

const msg = (over: Partial<Message> = {}): Message => ({
	id: 'm1',
	groupId: 'g1',
	authorId: null,
	kind: 'system',
	body: "Everyone's in for Thu 26 Nov · from 18:00",
	poll: null,
	proposalId: null,
	eventId: 'e1',
	createdAt: ago(60_000),
	editedAt: null,
	deleted: false,
	receipt: null,
	attachments: [],
	caption: null,
	replyTo: null,
	...over
});

describe('isConfirmationMessage', () => {
	test('a system message with an event', () => {
		expect(isConfirmationMessage(msg())).toBe(true);
	});
	test('not other system messages or event cards', () => {
		expect(isConfirmationMessage(msg({ eventId: null }))).toBe(false);
		expect(isConfirmationMessage(msg({ kind: 'event' }))).toBe(false);
		expect(isConfirmationMessage(msg({ kind: 'text', authorId: 'u1' }))).toBe(false);
	});
});

describe('isRecentConfirmation', () => {
	test('within the last 24 hours', () => {
		expect(isRecentConfirmation(ago(0), NOW)).toBe(true);
		expect(isRecentConfirmation(ago(BURST_WINDOW_MS - 1), NOW)).toBe(true);
	});
	test('not at or after 24 hours, or an unreadable date', () => {
		expect(isRecentConfirmation(ago(BURST_WINDOW_MS), NOW)).toBe(false);
		expect(isRecentConfirmation(ago(3 * BURST_WINDOW_MS), NOW)).toBe(false);
		expect(isRecentConfirmation('nope', NOW)).toBe(false);
	});
	test('a slightly future time (clock skew) counts as recent', () => {
		expect(isRecentConfirmation(ago(-30_000), NOW)).toBe(true);
	});
});

describe('claimBurst', () => {
	test('bursts once per message per session', () => {
		const seen = new Set<string>();
		expect(claimBurst(msg(), { now: NOW, seen })).toBe(true);
		expect(claimBurst(msg(), { now: NOW, seen })).toBe(false);
		expect(claimBurst(msg({ id: 'm2' }), { now: NOW, seen })).toBe(true);
	});
	test('not for old confirmations, non-confirmations or pending messages', () => {
		const seen = new Set<string>();
		expect(claimBurst(msg({ createdAt: ago(2 * BURST_WINDOW_MS) }), { now: NOW, seen })).toBe(false);
		expect(claimBurst(msg({ eventId: null }), { now: NOW, seen })).toBe(false);
		expect(claimBurst(msg({ id: 'temp-1' }), { now: NOW, seen })).toBe(false);
		expect(seen.size).toBe(0);
	});
	test('not with reduced motion, and that does not use up the burst', () => {
		const seen = new Set<string>();
		expect(claimBurst(msg(), { now: NOW, seen, reducedMotion: true })).toBe(false);
		expect(claimBurst(msg(), { now: NOW, seen })).toBe(true);
	});
	test('uses a shared session set by default', () => {
		const m = msg({ id: 'shared-1' });
		expect(claimBurst(m, { now: NOW })).toBe(true);
		expect(claimBurst(m, { now: NOW })).toBe(false);
	});
});
