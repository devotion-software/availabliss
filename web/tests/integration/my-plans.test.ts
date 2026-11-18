import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { createGroup } from '#lib/server/groups';
import { createEvent, myPlans } from '#lib/server/events';
import { confirmAnyway, createProposal, respond } from '#lib/server/proposals';
import { everyDay, fixture } from './fixtures';

const fx = fixture('myplans');
afterAll(() => fx.cleanup());

const at = (day: string, title: string) => ({
	title,
	start: `${day}T18:00:00.000Z`,
	end: `${day}T20:00:00.000Z`,
	allDay: false,
	location: null,
	note: null
});

describe('myPlans', () => {
	test('splits your plans across groups into going, can’t make it and past', async () => {
		const [me, friend, other] = await fx.users(3);
		const a = (await createGroup(friend, { name: 'A', requirement: everyDay('2032-01-01', '2032-12-31') }, 'UTC')).id;
		const b = (await createGroup(friend, { name: 'B', requirement: everyDay('2032-01-01', '2032-12-31') }, 'UTC')).id;
		await fx.join(a, me);
		await fx.join(b, me);
		await fx.join(a, other);

		await createEvent(a, friend, at('2032-06-01', 'Direct'));
		await createEvent(b, friend, at('2032-01-05', 'Old one'));
		const yes = await createProposal(a, friend, at('2032-06-10', 'Yes'));
		await respond(yes.id, me, 'accepted');
		await respond(yes.id, other, 'accepted');
		const no = await createProposal(a, friend, at('2032-06-12', 'No'));
		await respond(no.id, me, 'declined');
		await respond(no.id, other, 'accepted');
		await confirmAnyway(no.id, friend);

		const plans = await myPlans(me, new Date('2032-03-01T00:00:00Z'));
		expect(plans.going.map((p) => [p.title, p.myResponse, p.groupName])).toEqual([
			['Direct', null, 'A'],
			['Yes', 'accepted', 'A']
		]);
		expect(plans.notGoing.map((p) => p.title)).toEqual(['No']);
		expect(plans.past.map((p) => p.title)).toEqual(['Old one']);
	});
});
