import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import { and, eq } from 'drizzle-orm';
import type { Requirement } from '#lib/types';
import { db } from '#lib/server/db';
import { holidayCache, schoolHolidays } from '#lib/server/db/schema';
import { createGroup, getGroupRow } from '#lib/server/groups';
import { createBlock } from '#lib/server/blocks';
import { groupAvailability, groupDay, planningContext, previewRequirement, upcomingSlotCount } from '#lib/server/availability/service';
import { bankHolidays, schoolAuthorities, schoolHolidayRanges, subdivisions } from '#lib/server/holidays';
import { addDays } from '#lib/server/availability/time';
import { today } from '#lib/server/requirements';
import { newId } from '#lib/server/ids';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('availability');

/*
 * "ZZ" is a user-assigned ISO code no real country uses, so the cached bank holidays
 * below never clash with real data and are served from holiday_cache without a fetch.
 */
const COUNTRY = 'ZZ';
const AUTHORITY = `it-${newId()}`;
const THIS_YEAR = new Date().getUTCFullYear();

beforeAll(async () => {
	const rows = [
		{
			year: 2031,
			holidays: [
				{ date: '2031-03-05', name: 'Founders Day', subdivisions: null },
				{ date: '2031-03-06', name: 'North Day', subdivisions: ['ZZ-N'] }
			]
		},
		{ year: THIS_YEAR, holidays: [{ date: `${THIS_YEAR}-06-01`, name: 'South Day', subdivisions: ['ZZ-S', 'ZZ-N'] }] }
	];
	for (const r of rows) {
		await db
			.insert(holidayCache)
			.values({ country: COUNTRY, ...r, fetchedAt: new Date() })
			.onDuplicateKeyUpdate({ set: { holidays: r.holidays, fetchedAt: new Date() } });
	}
	await db.insert(schoolHolidays).values([
		{ id: newId(), country: COUNTRY, authority: AUTHORITY, authorityName: 'Test Borough', name: 'Spring break', start: '2031-03-07', end: '2031-03-08' },
		{ id: newId(), country: COUNTRY, authority: AUTHORITY, authorityName: 'Test Borough', name: 'Summer', start: '2031-07-20', end: '2031-08-31' }
	]);
});

afterAll(async () => {
	await db.delete(holidayCache).where(eq(holidayCache.country, COUNTRY));
	await db.delete(schoolHolidays).where(and(eq(schoolHolidays.country, COUNTRY), eq(schoolHolidays.authority, AUTHORITY)));
	await fx.cleanup();
});

describe('holidays (cached)', () => {
	const range = { from: '2031-03-01', to: '2031-03-31' };

	test('bank holidays apply nationally or to the matching subdivision', async () => {
		expect([...(await bankHolidays(COUNTRY, null, range))]).toEqual([['2031-03-05', 'Founders Day']]);
		expect(Object.fromEntries(await bankHolidays(COUNTRY, 'ZZ-N', range))).toEqual({ '2031-03-05': 'Founders Day', '2031-03-06': 'North Day' });
		expect((await bankHolidays(COUNTRY, null, { from: '2031-03-06', to: '2031-03-31' })).size).toBe(0);
		expect((await bankHolidays(null, null, range)).size).toBe(0);
	});

	test('subdivisions and school authorities', async () => {
		expect(await subdivisions(COUNTRY)).toEqual([
			{ code: 'ZZ-N', name: 'ZZ-N' },
			{ code: 'ZZ-S', name: 'ZZ-S' }
		]);
		expect(await schoolAuthorities(COUNTRY)).toContainEqual({ id: AUTHORITY, name: 'Test Borough' });
	});

	test('school holiday ranges overlapping the range, plus custom ones', async () => {
		const custom = [{ from: '2031-03-20', to: '2031-03-21' }];
		expect(await schoolHolidayRanges({ schoolAuthority: AUTHORITY, customSchoolHolidays: custom }, range)).toEqual([
			{ from: '2031-03-07', to: '2031-03-08', name: 'Spring break' },
			{ from: '2031-03-20', to: '2031-03-21', name: 'School holidays' }
		]);
		expect(await schoolHolidayRanges({ schoolAuthority: null, customSchoolHolidays: [] }, range)).toEqual([]);
	});
});

describe('groupAvailability', () => {
	let owner: string, member: string, outsider: string, groupId: string;

	beforeAll(async () => {
		owner = await fx.user({ country: COUNTRY });
		[member, outsider] = await fx.users(2);
		groupId = (await createGroup(owner, { name: 'Availability', requirement: { ...everyDay(), schoolAuthority: AUTHORITY } }, 'UTC')).id;
		await fx.join(groupId, member);
		await createBlock(member, { start: '2031-03-04T10:00:00Z', end: '2031-03-04T11:00:00Z', allDay: false, repeat: 'none', until: null, note: 'Private' });
	});

	test('a busy member blocks their day; unsynced members are pending', async () => {
		const a = await groupAvailability(groupId, owner, '2031-03-03', '2031-03-09');
		expect(a).toMatchObject({ from: '2031-03-03', to: '2031-03-09', memberCount: 2, syncedCount: 1, pendingMemberIds: [owner] });
		expect(a.visibleWeekdays).toEqual([0, 1, 2, 3, 4, 5, 6]);
		const day = Object.fromEntries(a.days.map((d) => [d.date, d]));
		expect(day['2031-03-04']).toMatchObject({ status: 'busy', busyMemberIds: [member], slotPosition: null });
		expect(day['2031-03-03']).toMatchObject({ status: 'match', slotPosition: 'single' });
		// Bank holidays follow the owner's region; school holidays the group's authority.
		expect(day['2031-03-05'].holiday).toBe('Founders Day');
		expect(day['2031-03-07'].holiday).toBe('Spring break');
		expect(a.slots.length).toBe(6);
		expect(a.slots.map((s) => s.start.slice(0, 10))).not.toContain('2031-03-04');
	});

	test('validates the range and membership', async () => {
		expect((await apiError(groupAvailability(groupId, owner, '2031-03-09', '2031-03-03'))).code).toBe('validation_error');
		expect((await apiError(groupAvailability(groupId, owner, '2031-01-01', '2031-06-01'))).code).toBe('validation_error');
		expect((await apiError(groupAvailability(groupId, outsider, '2031-03-03', '2031-03-09'))).code).toBe('not_found');
	});

	test('groupDay shows each member’s busy time without labels', async () => {
		const d = await groupDay(groupId, owner, '2031-03-04');
		expect(d.date).toBe('2031-03-04');
		const m = d.members.find((x) => x.userId === member)!;
		expect(m).toEqual({ userId: member, pending: false, busy: [{ start: '2031-03-04T10:00:00.000Z', end: '2031-03-04T11:00:00.000Z', manual: true }] });
		expect(d.members.find((x) => x.userId === owner)?.pending).toBe(true);
		expect(JSON.stringify(d)).not.toContain('Private');
		expect(d.everyoneFree).toEqual([
			{ start: '2031-03-04T00:00:00.000Z', end: '2031-03-04T10:00:00.000Z' },
			{ start: '2031-03-04T11:00:00.000Z', end: '2031-03-05T00:00:00.000Z' }
		]);
		expect((await apiError(groupDay(groupId, outsider, '2031-03-04'))).code).toBe('not_found');
	});

	test('planningContext: slots and holidays only, capped', async () => {
		const row = await getGroupRow(groupId);
		const ctx = await planningContext(row, 2);
		expect(ctx).toMatchObject({ from: '2031-03-03', to: '2031-03-09', members: 2, synced: 1 });
		expect(ctx.slots.length).toBe(2);
		expect(ctx.holidays).toEqual([
			{ date: '2031-03-05', name: 'Founders Day' },
			{ date: '2031-03-07', name: 'Spring break' },
			{ date: '2031-03-08', name: 'Spring break' }
		]);
		expect(JSON.stringify(ctx)).not.toContain(member);

		const past = { ...row, requirement: everyDay('2020-01-01', '2020-01-31') };
		expect(await planningContext(past)).toMatchObject({ slots: [], holidays: [], members: 0, synced: 0 });
	});
});

describe('upcomingSlotCount', () => {
	test('counts slots from today to the end of the range', async () => {
		// An owner with no region, so no bank holidays are fetched for the current year.
		const owner = await fx.user();
		const from = today('UTC');
		const group = await createGroup(owner, { name: 'Soon', requirement: everyDay(addDays(from, 1), addDays(from, 5)) }, 'UTC');
		const row = await getGroupRow(group.id);
		expect(await upcomingSlotCount(row)).toBe(5);
		expect(await upcomingSlotCount({ ...row, requirement: everyDay('2020-01-01', '2020-01-31') })).toBe(0);
	});

	test('counts a range that starts months from now', async () => {
		const owner = await fx.user();
		const start = addDays(today('UTC'), 200);
		const group = await createGroup(owner, { name: 'Later', requirement: everyDay(start, addDays(start, 2)) }, 'UTC');
		expect(await upcomingSlotCount(await getGroupRow(group.id))).toBe(3);
	});
});

describe('previewRequirement', () => {
	test('counts slots before anyone’s busy time, from today at the earliest', async () => {
		const me = await fx.user();
		expect(await previewRequirement(me, everyDay())).toEqual({ count: 7, from: '2031-03-03', to: '2031-03-09' });

		// A range that started in the past is clipped to today.
		const from = today('UTC');
		const started: Requirement = everyDay(addDays(from, -10), addDays(from, 10));
		const preview = await previewRequirement(me, started);
		expect(preview.from).toBe(from);
		expect(preview.to).toBe(addDays(from, 10));
		expect(preview.count).toBeGreaterThanOrEqual(10);

		expect(await previewRequirement(me, everyDay('2020-01-01', '2020-01-02'))).toMatchObject({ count: 0 });
	});

	test('bank holidays come from the editing user’s region', async () => {
		const me = await fx.user({ country: COUNTRY });
		// Only Wednesdays, but bank holidays count too: 2031-03-05 is both.
		const weds: Requirement = { ...everyDay('2031-03-03', '2031-03-31'), days: [2], bankHolidays: true };
		expect((await previewRequirement(me, weds)).count).toBe(4);
	});
});
