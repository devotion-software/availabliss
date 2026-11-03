import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { db } from '#lib/server/db';
import { busyIntervals, calendarConnections, calendars } from '#lib/server/db/schema';
import { createBlock, deleteBlock, getBlock, listBlocks, updateBlock } from '#lib/server/blocks';
import { busyDuring, loadBusy, myBusy, syncedUserIds } from '#lib/server/busy';
import { newId } from '#lib/server/ids';
import { apiError, fixture } from './fixtures';

const fx = fixture('busy');
afterAll(() => fx.cleanup());

const at = (iso: string) => Date.parse(iso);
const block = (start: string, end: string, extra: Partial<Parameters<typeof createBlock>[1]> = {}) => ({
	start,
	end,
	allDay: false,
	repeat: 'none' as const,
	until: null,
	note: null,
	...extra
});

/** A calendar connection with one calendar and some busy intervals. */
async function calendarWith(userId: string, name: string, busy: boolean, spans: [string, string][]) {
	const connectionId = newId();
	const calendarId = newId();
	await db.insert(calendarConnections).values({ id: connectionId, userId, provider: 'caldav', label: `Test ${name}`, credentials: null });
	await db.insert(calendars).values({ id: calendarId, connectionId, externalId: name, name, busy });
	if (spans.length) {
		await db.insert(busyIntervals).values(
			spans.map(([s, e]) => ({ id: newId(), userId, calendarId, start: new Date(s), end: new Date(e) }))
		);
	}
	return calendarId;
}

describe('blocks CRUD', () => {
	test('create, read, list newest first, update and delete', async () => {
		const me = await fx.user();
		const a = await createBlock(me, block('2031-01-05T09:00:00.000Z', '2031-01-05T10:00:00.000Z', { note: 'Dentist' }));
		const b = await createBlock(me, block('2031-01-06T00:00:00.000Z', '2031-01-07T00:00:00.000Z', { allDay: true, repeat: 'weekly', until: '2031-02-01' }));
		expect(a).toMatchObject({ start: '2031-01-05T09:00:00.000Z', end: '2031-01-05T10:00:00.000Z', allDay: false, repeat: 'none', until: null, note: 'Dentist' });
		expect(b).toMatchObject({ allDay: true, repeat: 'weekly', until: '2031-02-01' });
		expect((await listBlocks(me)).map((x) => x.id)).toEqual([b.id, a.id]);
		expect(await getBlock(me, a.id)).toEqual(a);

		const moved = await updateBlock(me, a.id, { start: '2031-01-05T11:00:00.000Z', end: '2031-01-05T12:30:00.000Z', note: null });
		expect(moved).toMatchObject({ start: '2031-01-05T11:00:00.000Z', end: '2031-01-05T12:30:00.000Z', note: null });
		// A partial patch is checked against the stored start/end.
		expect((await apiError(updateBlock(me, a.id, { end: '2031-01-05T10:00:00.000Z' }))).code).toBe('validation_error');
		expect((await apiError(updateBlock(me, a.id, { start: '2031-01-05T12:30:00.000Z' }))).code).toBe('validation_error');

		await deleteBlock(me, a.id);
		expect((await apiError(getBlock(me, a.id))).code).toBe('not_found');
		expect((await listBlocks(me)).map((x) => x.id)).toEqual([b.id]);
	});

	test("users cannot see or change each other's blocks", async () => {
		const [me, other] = await fx.users(2);
		const mine = await createBlock(me, block('2031-01-05T09:00:00Z', '2031-01-05T10:00:00Z'));
		expect((await apiError(getBlock(other, mine.id))).code).toBe('not_found');
		expect((await apiError(updateBlock(other, mine.id, { note: 'hijack' }))).code).toBe('not_found');
		expect((await apiError(deleteBlock(other, mine.id))).code).toBe('not_found');
		expect(await listBlocks(other)).toEqual([]);
		expect((await getBlock(me, mine.id)).note).toBeNull();
	});
});

describe('loadBusy', () => {
	test('merges calendar busy time (busy calendars only) with expanded manual blocks, sorted', async () => {
		const me = await fx.user({ timezone: 'Europe/London' });
		const work = await calendarWith(me, 'Work', true, [
			['2031-03-03T09:00:00Z', '2031-03-03T17:00:00Z'],
			['2031-04-01T09:00:00Z', '2031-04-01T17:00:00Z'] // outside the range
		]);
		await calendarWith(me, 'Birthdays', false, [['2031-03-04T00:00:00Z', '2031-03-05T00:00:00Z']]);
		const gym = await createBlock(me, block('2031-02-17T18:00:00Z', '2031-02-17T19:00:00Z', { repeat: 'weekly', note: 'Gym' }));
		await createBlock(me, block('2031-03-05T12:00:00Z', '2031-03-05T13:00:00Z'));

		const from = at('2031-03-03T00:00:00Z');
		const to = at('2031-03-10T00:00:00Z');
		const busy = (await loadBusy([me], from, to)).get(me)!;

		expect(busy.map((b) => new Date(b.start).toISOString())).toEqual([
			'2031-03-03T09:00:00.000Z',
			'2031-03-03T18:00:00.000Z',
			'2031-03-05T12:00:00.000Z'
		]);
		expect(busy[0]).toMatchObject({ manual: false, label: 'Work', calendarId: work, blockId: null });
		expect(busy[1]).toMatchObject({ manual: true, label: 'Gym', calendarId: null, blockId: gym.id, end: at('2031-03-03T19:00:00Z') });

		// After the clocks go forward a London weekly block keeps its 18:00 local time.
		const summer = (await loadBusy([me], at('2031-03-31T00:00:00Z'), at('2031-04-01T00:00:00Z'))).get(me)!;
		expect(summer.filter((b) => b.manual).map((b) => new Date(b.start).toISOString())).toEqual(['2031-03-31T17:00:00.000Z']);

		const mine = await myBusy(me, from, to);
		expect(mine[0]).toEqual({ start: '2031-03-03T09:00:00.000Z', end: '2031-03-03T17:00:00.000Z', source: 'calendar', label: 'Work', calendarId: work, blockId: null });
		expect(mine[1]).toMatchObject({ source: 'manual', label: 'Gym', blockId: gym.id });
	});

	test('repeats honour `until`, fortnightly skips weeks, and every requested user gets an entry', async () => {
		const [me, idle] = await fx.users(2);
		await createBlock(me, block('2031-03-03T10:00:00Z', '2031-03-03T11:00:00Z', { repeat: 'fortnightly', until: '2031-04-01' }));
		const busy = await loadBusy([me, idle], at('2031-03-01T00:00:00Z'), at('2031-05-01T00:00:00Z'));
		expect(busy.get(idle)).toEqual([]);
		expect(busy.get(me)!.map((b) => new Date(b.start).toISOString().slice(0, 10))).toEqual(['2031-03-03', '2031-03-17', '2031-03-31']);
		expect((await loadBusy([], 0, 1)).size).toBe(0);
	});

	test('busyDuring returns only the ids of members with an overlap', async () => {
		const [a, b, c] = await fx.users(3);
		await createBlock(a, block('2031-06-01T10:00:00Z', '2031-06-01T12:00:00Z'));
		await createBlock(b, block('2031-06-01T12:00:00Z', '2031-06-01T13:00:00Z')); // touches the end only
		await calendarWith(c, 'Work', true, [['2031-06-01T11:59:00Z', '2031-06-01T12:01:00Z']]);
		expect((await busyDuring([a, b, c], at('2031-06-01T11:00:00Z'), at('2031-06-01T12:00:00Z'))).sort()).toEqual([a, c].sort());
	});

	test('syncedUserIds: a calendar connection or any manual block counts', async () => {
		const [cal, manual, neither] = await fx.users(3);
		await calendarWith(cal, 'Home', false, []);
		await createBlock(manual, block('2031-01-01T00:00:00Z', '2031-01-01T01:00:00Z'));
		const synced = await syncedUserIds([cal, manual, neither]);
		expect([...synced].sort()).toEqual([cal, manual].sort());
		expect((await syncedUserIds([])).size).toBe(0);
	});
});
