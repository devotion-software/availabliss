import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { createGroup } from '#lib/server/groups';
import { createEvent, deleteEvent, updateEvent } from '#lib/server/events';
import { createProposal, respond, confirmAnyway } from '#lib/server/proposals';
import { getCalendarFeed, renderCalendarFeed, resetCalendarFeed } from '#lib/server/calendar-feeds';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('feeds');
afterAll(() => fx.cleanup());

const tokenOf = (url: string) => url.split('/calendar/')[1].replace('.ics', '');
const event = (title: string, day: string) => ({ title, start: `${day}T18:00:00.000Z`, end: `${day}T20:00:00.000Z`, allDay: false, location: null, note: null });

describe('calendar feeds', () => {
	test('lists your groups’ plans, follows edits and deletions, and leaves out ones you can’t make', async () => {
		const [owner, member] = await fx.users(2);
		const groupId = (await createGroup(owner, { name: 'Supper', requirement: everyDay('2032-05-01', '2032-06-30') }, 'UTC')).id;
		await fx.join(groupId, member);
		const other = (await createGroup(owner, { name: 'Walks', requirement: everyDay('2032-05-01', '2032-06-30') }, 'UTC')).id;

		const feed = await getCalendarFeed(member);
		expect(feed.webcalUrl.startsWith('webcal://')).toBe(true);
		expect((await getCalendarFeed(member)).url).toBe(feed.url);
		const token = tokenOf(feed.url);

		const swap = await createEvent(groupId, owner, event('Book swap', '2032-05-10'));
		await createEvent(other, owner, event('Not my group', '2032-05-11'));
		let ics = await renderCalendarFeed(token, null, 'test.host');
		expect(ics).toContain(`UID:${swap.id}@test.host`);
		expect(ics).toContain('SUMMARY:Book swap');
		expect(ics).not.toContain('Not my group');
		const seq = Number(/SEQUENCE:(\d+)/.exec(ics)![1]);

		await new Promise((r) => setTimeout(r, 1100));
		await updateEvent(swap.id, owner, { title: 'Book swap (moved)' });
		ics = await renderCalendarFeed(token, null, 'test.host');
		expect(ics).toContain('SUMMARY:Book swap (moved)');
		expect(Number(/SEQUENCE:(\d+)/.exec(ics)![1])).toBeGreaterThan(seq);

		// A plan they said they can't make (confirmed anyway without them) stays out; deleted plans drop out.
		const third = await fx.user();
		await fx.join(groupId, third);
		const p = await createProposal(groupId, owner, { ...event('Curry', '2032-05-20'), title: 'Curry' });
		await respond(p.id, member, 'declined');
		await respond(p.id, third, 'accepted');
		await confirmAnyway(p.id, owner);
		await deleteEvent(swap.id, owner);
		ics = await renderCalendarFeed(token, null, 'test.host');
		expect(ics).not.toContain('Book swap');
		expect(ics).not.toContain('SUMMARY:Curry');
		expect(await renderCalendarFeed(tokenOf((await getCalendarFeed(third)).url), null, 'test.host')).toContain('SUMMARY:Curry');

		// One group only.
		expect(await renderCalendarFeed(token, groupId, 'test.host')).toContain('X-WR-CALNAME:Supper · Availabliss');
		expect((await apiError(renderCalendarFeed(token, other, 'test.host'))).code).toBe('not_found');
	});

	test('resetting the link stops the old one working', async () => {
		const me = await fx.user();
		const old = tokenOf((await getCalendarFeed(me)).url);
		const fresh = tokenOf((await resetCalendarFeed(me)).url);
		expect(fresh).not.toBe(old);
		expect((await apiError(renderCalendarFeed(old, null, 'h'))).code).toBe('not_found');
		expect(await renderCalendarFeed(fresh, null, 'h')).toContain('BEGIN:VCALENDAR');
	});
});
