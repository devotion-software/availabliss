import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { and, eq, inArray } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { notifications } from '#lib/server/db/schema';
import { createGroup } from '#lib/server/groups';
import { createEvent } from '#lib/server/events';
import { createProposal, respond } from '#lib/server/proposals';
import { dueReminders, reminderText, sendDueReminders } from '#lib/server/reminders';
import { updateMe } from '#lib/server/users';
import { everyDay, fixture } from './fixtures';

const fx = fixture('reminders');
afterAll(() => fx.cleanup());

const at = (iso: string) => Date.parse(iso);
const remindersFor = async (userIds: string[]) =>
	db
		.select({ userId: notifications.userId, body: notifications.body, data: notifications.data })
		.from(notifications)
		.where(and(inArray(notifications.userId, userIds), eq(notifications.kind, 'reminder')));

describe('when reminders are due', () => {
	const start = at('2032-05-14T19:00:00Z');
	test('timed plans: the day before, then an hour before; none once started', () => {
		expect(dueReminders(start, false, start - 25 * 3600_000)).toEqual([]);
		expect(dueReminders(start, false, start - 23 * 3600_000)).toEqual(['day']);
		expect(dueReminders(start, false, start - 2 * 3600_000)).toEqual([]);
		expect(dueReminders(start, false, start - 30 * 60_000)).toEqual(['hour']);
		expect(dueReminders(start, false, start + 60_000)).toEqual([]);
	});
	test('all-day plans: 09:00 the day before (UTC midnight start), no hour-before', () => {
		const day = at('2032-05-14T00:00:00Z');
		expect(dueReminders(day, true, at('2032-05-13T08:00:00Z'))).toEqual([]);
		expect(dueReminders(day, true, at('2032-05-13T09:05:00Z'))).toEqual(['day']);
		expect(dueReminders(day, true, at('2032-05-13T23:30:00Z'))).toEqual(['day']);
	});
	test('wording', () => {
		expect(reminderText('day', 'Curry night', start, false, start - 23 * 3600_000, 'UTC')).toBe('Curry night is tomorrow at 19:00');
		expect(reminderText('day', 'Curry night', start, false, start - 5 * 3600_000, 'UTC')).toBe('Curry night is today at 19:00');
		expect(reminderText('hour', 'Curry night', start, false, start - 3600_000, 'UTC')).toBe('Curry night starts at 19:00');
		expect(reminderText('day', 'Lakes', at('2032-05-14T00:00:00Z'), true, at('2032-05-13T10:00:00Z'), 'UTC')).toBe('Lakes is tomorrow');
	});
});

describe('sendDueReminders', () => {
	test('reminds the people going, once each, as they chose; a maybe counts as going', async () => {
		const [owner, yes, maybe, no, quiet] = await fx.users(5);
		const groupId = (await createGroup(owner, { name: 'Supper', requirement: everyDay('2032-05-01', '2032-06-30') }, 'UTC')).id;
		for (const id of [yes, maybe, no, quiet]) await fx.join(groupId, id);
		const p = await createProposal(groupId, owner, {
			start: '2032-05-20T19:00:00.000Z',
			end: '2032-05-20T22:00:00.000Z',
			allDay: false,
			title: 'Curry night',
			location: null,
			note: null
		});
		await respond(p.id, yes, 'accepted');
		await respond(p.id, maybe, 'maybe');
		await respond(p.id, no, 'declined');
		await respond(p.id, quiet, 'accepted');
		await updateMe(quiet, { eventReminders: 'hour' });
		// Everyone but "no" and "maybe" said yes: confirm it anyway (owner + yes + quiet).
		const { confirmAnyway } = await import('#lib/server/proposals');
		await confirmAnyway(p.id, owner);

		await sendDueReminders(at('2032-05-19T20:00:00Z'));
		await sendDueReminders(at('2032-05-19T20:05:00Z'));
		let got = await remindersFor([owner, yes, maybe, no, quiet]);
		expect(got.map((r) => r.userId).sort()).toEqual([owner, yes, maybe].sort());
		expect(got[0].body).toBe('Curry night is tomorrow at 19:00');
		expect(got[0].data).toMatchObject({ reminder: 'day', proposalId: p.id });

		await sendDueReminders(at('2032-05-20T18:10:00Z'));
		got = await remindersFor([quiet]);
		expect(got.map((r) => r.body)).toEqual(['Curry night starts at 19:00']);
	});

	test('an event added straight to the calendar reminds every member', async () => {
		const [owner, member] = await fx.users(2);
		const groupId = (await createGroup(owner, { name: 'Book club', requirement: everyDay('2032-05-01', '2032-06-30') }, 'UTC')).id;
		await fx.join(groupId, member);
		await createEvent(groupId, owner, {
			title: 'Book swap',
			start: '2032-06-02T18:00:00.000Z',
			end: '2032-06-02T20:00:00.000Z',
			allDay: false,
			location: null,
			note: null
		});
		await sendDueReminders(at('2032-06-02T17:15:00Z'));
		expect((await remindersFor([owner, member])).map((r) => r.userId).sort()).toEqual([owner, member].sort());
	});

	test('nudges members who haven’t replied a day before an open proposal, once', async () => {
		const [owner, replied, waiting] = await fx.users(3);
		const groupId = (await createGroup(owner, { name: 'Walks', requirement: everyDay('2032-05-01', '2032-06-30') }, 'UTC')).id;
		await fx.join(groupId, replied);
		await fx.join(groupId, waiting);
		const p = await createProposal(groupId, owner, {
			start: '2032-06-10T10:00:00.000Z',
			end: '2032-06-10T12:00:00.000Z',
			allDay: false,
			title: null,
			location: null,
			note: null
		});
		await respond(p.id, replied, 'maybe');
		await sendDueReminders(at('2032-06-09T12:00:00Z'));
		await sendDueReminders(at('2032-06-09T12:05:00Z'));
		const got = await remindersFor([owner, replied, waiting]);
		expect(got.map((r) => r.userId)).toEqual([waiting]);
		expect(got[0].body).toMatch(/is tomorrow\. Can you make it\?/);
		expect(got[0].data).toMatchObject({ reminder: 'reply', proposalId: p.id });
	});
});
