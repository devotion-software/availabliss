import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import { eq } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { devices } from '#lib/server/db/schema';
import { createGroup } from '#lib/server/groups';
import { createEvent, deleteEvent, getEvent, listEvents, updateEvent, upcomingFor } from '#lib/server/events';
import { closePoll, listMessages, markReceipts, messageInfo, sendPoll, sendText, vote } from '#lib/server/chat';
import { groupSummaries } from '#lib/server/group-summaries';
import { newId } from '#lib/server/ids';
import { messageReceipts, messages } from '#lib/server/db/schema';
import { listNotifications, markRead, registerDevice, unregisterDevice } from '#lib/server/notifications';
import { apiError, everyDay, fixture } from './fixtures';

const unreadCount = async (groupId: string, userId: string) =>
	(await groupSummaries(userId)).find((g) => g.id === groupId)?.unreadCount ?? 0;

const fx = fixture('events');
afterAll(() => fx.cleanup());

let owner: string, admin: string, member: string, outsider: string, groupId: string;

beforeAll(async () => {
	owner = await fx.user({ name: 'Olive' });
	[admin, member, outsider] = await fx.users(3);
	groupId = (await createGroup(owner, { name: 'Events & chat', requirement: everyDay() }, 'UTC')).id;
	await fx.join(groupId, admin, 'admin');
	await fx.join(groupId, member);
});

const inDays = (d: number, h = 18) => new Date(Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth(), new Date().getUTCDate() + d, h)).toISOString();

describe('events', () => {
	test('create notifies the others; members read, creator or admin edit', async () => {
		const e = await createEvent(groupId, member, { title: 'Picnic', start: inDays(3), end: inDays(3, 20), allDay: false, location: 'Park', note: null });
		expect(e).toMatchObject({ groupId, title: 'Picnic', location: 'Park', createdBy: member, proposalId: null });
		expect(await getEvent(e.id, owner)).toEqual(e);
		expect((await apiError(getEvent(e.id, outsider))).code).toBe('not_found');

		const { notifications } = await listNotifications(owner);
		expect(notifications.find((n) => n.kind === 'event.created')).toMatchObject({ groupId, title: 'Events & chat', data: expect.objectContaining({ eventId: e.id }) });
		expect((await listNotifications(member)).notifications.some((n) => n.kind === 'event.created')).toBe(false);

		const moved = await updateEvent(e.id, admin, { start: inDays(4), end: inDays(4, 21), title: 'Picnic (moved)' });
		expect(moved).toMatchObject({ title: 'Picnic (moved)', start: inDays(4), end: inDays(4, 21), location: 'Park' });
		// Only one end sent: checked against the stored other end.
		expect((await apiError(updateEvent(e.id, admin, { start: inDays(4, 22) }))).code).toBe('validation_error');
		expect((await apiError(updateEvent(e.id, admin, { end: inDays(4, 17) }))).code).toBe('validation_error');

		const other = await createEvent(groupId, owner, { title: 'Owner’s', start: inDays(5), end: inDays(5, 19), allDay: false, location: null, note: null });
		expect((await apiError(updateEvent(other.id, member, { title: 'Mine now' }))).code).toBe('forbidden');
		expect((await apiError(deleteEvent(other.id, member))).code).toBe('forbidden');

		const listed = await listEvents(groupId, member, new Date(inDays(0)), new Date(inDays(10)));
		expect(listed.map((x) => x.id)).toEqual([e.id, other.id]);
		expect(await listEvents(groupId, member, new Date(inDays(4, 22)), new Date(inDays(5, 0)))).toEqual([]);

		const upcoming = await upcomingFor(member);
		expect(upcoming.map((x) => x.id)).toEqual([e.id, other.id]);
		expect(upcoming[0].groupName).toBe('Events & chat');
		expect(await upcomingFor(outsider)).toEqual([]);

		await deleteEvent(other.id, owner);
		expect((await apiError(getEvent(other.id, owner))).code).toBe('not_found');
	});
});

describe('chat', () => {
	test('text messages, paging, receipts and unread counts', async () => {
		const first = await sendText(groupId, owner, 'Hello');
		const second = await sendText(groupId, member, 'Hi!');
		const third = await sendText(groupId, owner, 'Who’s in?');
		expect((await apiError(sendText(groupId, outsider, 'Let me in'))).code).toBe('not_found');

		const latest = await listMessages(groupId, admin, null, 2);
		expect(latest.messages.map((m) => m.id)).toEqual([second.id, third.id]);
		const older = await listMessages(groupId, admin, second.id, 50);
		expect(older.messages.map((m) => m.id)).toContain(first.id);
		expect(older.messages.map((m) => m.id)).not.toContain(second.id);
		expect((await apiError(listMessages(groupId, admin, 'missing', 10))).code).toBe('not_found');

		const before = await unreadCount(groupId, admin);
		expect(before).toBeGreaterThanOrEqual(3);
		await markReceipts(groupId, admin, second.id, 'seen');
		await markReceipts(groupId, member, third.id, 'delivered');
		expect(await unreadCount(groupId, admin)).toBe(1);

		const info = await messageInfo(first.id, owner);
		expect(info.seen.map((s) => s.userId)).toEqual([admin]);
		expect(info.delivered.map((s) => s.userId)).toEqual([member]);
		expect(info.pending).toEqual([]);
		expect((await apiError(messageInfo(first.id, member))).code).toBe('forbidden');

		// The author's own view carries receipt counts.
		const own = (await listMessages(groupId, owner, null, 50)).messages.find((m) => m.id === first.id)!;
		expect(own.receipt).toMatchObject({ delivered: 2, seen: 1, recipients: 2, seenBy: [admin] });
	});

	test('unread counts skip history from before joining', async () => {
		const g = (await createGroup(owner, { name: 'Late joiner', requirement: everyDay() }, 'UTC')).id;
		await sendText(g, owner, 'Before you joined');
		await fx.join(g, member);
		expect(await unreadCount(g, member)).toBe(0);
		// History is still readable.
		expect((await listMessages(g, member, null, 10)).messages).toHaveLength(1);
		await sendText(g, owner, 'Welcome!');
		expect(await unreadCount(g, member)).toBe(1);
	});

	test('marking as seen covers every earlier message, not just the newest 500', async () => {
		const g = (await createGroup(owner, { name: 'Busy chat', requirement: everyDay() }, 'UTC')).id;
		await fx.join(g, member);
		// Insert straight into the table: 620 messages through sendText would be slow.
		await db.insert(messages).values(Array.from({ length: 620 }, (_, i) => ({ id: newId(), groupId: g, authorId: owner, kind: 'text' as const, body: `m${i}` })));
		const last = await sendText(g, owner, 'Last one');
		expect(await unreadCount(g, member)).toBe(621);
		await markReceipts(g, member, last.id, 'delivered');
		await markReceipts(g, member, last.id, 'seen');
		expect(await unreadCount(g, member)).toBe(0);
		const rows = await db.select().from(messageReceipts).where(eq(messageReceipts.userId, member));
		expect(rows.filter((r) => r.seenAt).length).toBeGreaterThanOrEqual(621);
	});

	test('polls: single choice, multi choice and closing', async () => {
		const msg = await sendPoll(groupId, member, { question: 'Where?', options: ['Beach', 'Hills', 'Pub'], multi: false });
		const poll = msg.poll!;
		expect(poll).toMatchObject({ question: 'Where?', multi: false, closed: false });
		const [beach, hills] = poll.options.map((o) => o.id);
		expect(poll.options.map((o) => o.label)).toEqual(['Beach', 'Hills', 'Pub']);

		expect((await apiError(vote(poll.id, owner, [beach, hills]))).code).toBe('validation_error');
		expect((await apiError(vote(poll.id, owner, ['nope']))).code).toBe('validation_error');
		expect((await apiError(vote(poll.id, outsider, [beach]))).code).toBe('not_found');

		// A repeated option is one vote, not a primary-key error.
		expect((await vote(poll.id, member, [beach, beach])).options.find((o) => o.id === beach)!.voterIds).toEqual([member]);
		await vote(poll.id, member, []);
		await vote(poll.id, owner, [beach]);
		const changed = await vote(poll.id, owner, [hills]);
		expect(changed.options.find((o) => o.id === hills)!.voterIds).toEqual([owner]);
		expect(changed.options.find((o) => o.id === beach)!.voterIds).toEqual([]);
		expect((await vote(poll.id, owner, [])).options.every((o) => o.voterIds.length === 0)).toBe(true);

		expect((await apiError(closePoll(poll.id, outsider))).code).toBe('not_found');
		const other = await sendPoll(groupId, owner, { question: 'When?', options: ['Sat', 'Sun'], multi: true });
		expect((await apiError(closePoll(other.poll!.id, member))).code).toBe('forbidden');
		expect((await vote(other.poll!.id, member, other.poll!.options.map((o) => o.id))).options.every((o) => o.voterIds.includes(member))).toBe(true);

		expect((await closePoll(poll.id, member)).closed).toBe(true);
		expect((await apiError(vote(poll.id, owner, [beach]))).message).toContain('closed');
	});
});

describe('notifications and devices', () => {
	test('mark some or all read', async () => {
		const { notifications, unread } = await listNotifications(owner);
		expect(unread).toBe(notifications.length);
		expect(unread).toBeGreaterThan(0);
		await markRead(owner, [notifications[0].id]);
		expect((await listNotifications(owner)).unread).toBe(unread - 1);
		await markRead(owner);
		const after = await listNotifications(owner);
		expect(after.unread).toBe(0);
		expect(after.notifications.every((n) => n.readAt !== null)).toBe(true);
	});

	test('a device token moves to whoever registered it last', async () => {
		const token = `it-token-${crypto.randomUUID()}`;
		await registerDevice(owner, { platform: 'ios', token, name: 'iPhone' });
		await registerDevice(member, { platform: 'android', token, name: 'Pixel' });
		const row = await db.query.devices.findFirst({ where: eq(devices.token, token) });
		expect(row).toMatchObject({ userId: member, platform: 'android', name: 'Pixel' });

		await unregisterDevice(owner, token); // not theirs any more: no-op
		expect(await db.query.devices.findFirst({ where: eq(devices.token, token) })).toBeTruthy();
		await unregisterDevice(member, token);
		expect(await db.query.devices.findFirst({ where: eq(devices.token, token) })).toBeUndefined();
	});
});
