import './setup';
import { afterAll, beforeAll, beforeEach, describe, expect, test } from 'bun:test';
import { asc, eq } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { busyIntervals, calendarConnections, calendars } from '#lib/server/db/schema';
import { encryptJson } from '#lib/server/crypto';
import { newId } from '#lib/server/ids';
import { adapters, horizonFor, planCoverage, refreshCoverage, syncConnection } from '#lib/server/calendars';
import type { CalendarAdapter, ChangedEvents, RemoteBusy, RemoteCalendar } from '#lib/server/calendars/types';
import { createGroup, updateGroup } from '#lib/server/groups';
import { loadBusy } from '#lib/server/busy';
import { everyDay, fixture } from './fixtures';

const fx = fixture('calendar-sync');
const DAY = 24 * 60 * 60 * 1000;
const YEAR = 365 * DAY;

/** A provider whose calendars and events the test controls, recording what it was asked. */
const fake = {
	calendars: [] as RemoteCalendar[],
	events: [] as RemoteBusy[],
	/** What `changes` answers (null = cursor no longer accepted). */
	changed: new Map() as ChangedEvents | null,
	reads: [] as { calendars: string[]; from: Date; to: Date }[],
	changeCalls: [] as { calendar: string; since: Date; to: Date }[]
};
const adapter: CalendarAdapter<object> = {
	listCalendars: async () => fake.calendars.map((c) => ({ ...c })),
	busy: async (_creds, cals, from, to) => {
		fake.reads.push({ calendars: cals.map((c) => c.externalId), from, to });
		return fake.events.filter((e) => cals.some((c) => c.externalId === e.calendarExternalId) && e.start < to && e.end > from);
	},
	changes: async (_creds, cal, since, _from, to) => {
		fake.changeCalls.push({ calendar: cal.externalId, since, to });
		return fake.changed;
	}
};

const real = adapters.caldav;
beforeAll(() => {
	adapters.caldav = adapter as typeof real;
});
afterAll(async () => {
	adapters.caldav = real;
	await fx.cleanup();
});
beforeEach(() => {
	fake.calendars = [];
	fake.events = [];
	fake.changed = new Map();
	fake.reads = [];
	fake.changeCalls = [];
});

async function connection(userId: string): Promise<string> {
	const id = newId();
	await db.insert(calendarConnections).values({
		id,
		userId,
		provider: 'caldav',
		label: `Test ${id}`,
		credentials: encryptJson({ serverUrl: 'https://dav.invalid', username: 'u', password: 'p' }, `calendar:${id}`)
	});
	return id;
}

const conn = async (id: string) => (await db.query.calendarConnections.findFirst({ where: eq(calendarConnections.id, id) }))!;
const rows = async (userId: string) =>
	db.select().from(busyIntervals).where(eq(busyIntervals.userId, userId)).orderBy(asc(busyIntervals.start));
const busy = (calendar: string, start: string, hours = 1, eventKey?: string): RemoteBusy => ({
	calendarExternalId: calendar,
	eventKey,
	start: new Date(start),
	end: new Date(Date.parse(start) + hours * 60 * 60 * 1000)
});
/** A date this many years from now, as an ISO string at 09:00 UTC. */
const yearsAhead = (n: number) => `${new Date(Date.now() + n * YEAR).toISOString().slice(0, 10)}T09:00:00.000Z`;

describe('planCoverage', () => {
	const now = Date.parse('2026-11-03T12:00:00Z');
	const horizon = new Date(now + 3 * YEAR);

	test('a first sync covers a step past the horizon and reads the far tier', () => {
		const c = planCoverage({ coveredUntil: null, farSyncedAt: null }, horizon, now);
		expect(c.until.getTime()).toBe(horizon.getTime() + 30 * DAY);
		expect(c.from.getTime()).toBe(now - 7 * DAY);
		expect(c.nearEnd.getTime()).toBe(now + 183 * DAY);
		expect(c.farDue).toBe(true);
	});

	test('coverage stays put until the horizon passes it, then grows by a step', () => {
		const covered = new Date(horizon.getTime() + 10 * DAY);
		const kept = planCoverage({ coveredUntil: covered, farSyncedAt: new Date(now - 60_000) }, horizon, now);
		expect(kept.until).toEqual(covered);
		expect(kept.farDue).toBe(false);
		const grown = planCoverage({ coveredUntil: new Date(horizon.getTime() - DAY), farSyncedAt: new Date(now - 60_000) }, horizon, now);
		expect(grown.until.getTime()).toBe(horizon.getTime() + 30 * DAY);
		expect(grown.farDue).toBe(true);
	});

	test('shrinks once the horizon has dropped well below what is covered', () => {
		const c = planCoverage({ coveredUntil: new Date(now + 9 * YEAR), farSyncedAt: new Date(now) }, horizon, now);
		expect(c.until.getTime()).toBe(horizon.getTime() + 30 * DAY);
	});

	test('the far tier is re-read daily', () => {
		const covered = new Date(horizon.getTime() + 10 * DAY);
		expect(planCoverage({ coveredUntil: covered, farSyncedAt: new Date(now - 25 * 60 * 60 * 1000) }, horizon, now).farDue).toBe(true);
	});
});

describe('horizonFor', () => {
	test('three years by default, further for far-off groups, capped at ten', async () => {
		const me = await fx.user();
		const now = Date.now();
		expect((await horizonFor(me, now)).getTime()).toBe(now + 3 * 365 * DAY);

		const to = new Date(now + 6 * YEAR).toISOString().slice(0, 10);
		await createGroup(me, { name: 'Far', requirement: everyDay('2027-01-01', to) }, 'UTC');
		expect((await horizonFor(me, now)).getTime()).toBe(Date.parse(`${to}T00:00:00Z`) + 15 * DAY);

		await createGroup(me, { name: 'Very far', requirement: everyDay('2027-01-01', '2050-01-01') }, 'UTC');
		expect((await horizonFor(me, now)).getTime()).toBe(now + 10 * 365 * DAY);
	});
});

describe('syncConnection', () => {
	test('first sync stores busy time years ahead, including past 2038', async () => {
		const me = await fx.user();
		const id = await connection(me);
		fake.calendars = [{ externalId: 'home', name: 'Home', color: null, changes: 'version', version: 'v1' }];
		const wedding = busy('home', yearsAhead(2.5), 8);
		fake.events = [busy('home', yearsAhead(0.1)), wedding];

		await syncConnection(id);
		const c = await conn(id);
		expect(c.status).toBe('ok');
		expect(c.coveredUntil!.getTime()).toBeGreaterThan(Date.now() + 3 * YEAR);
		expect(fake.reads).toHaveLength(1);
		expect(await rows(me)).toHaveLength(2);

		const loaded = await loadBusy([me], wedding.start.getTime() - DAY, wedding.end.getTime() + DAY);
		expect(loaded.get(me)!.map((b) => b.start)).toEqual([wedding.start.getTime()]);

		// DATETIME columns go past TIMESTAMP's 2038 limit.
		const [cal] = await db.select().from(calendars).where(eq(calendars.connectionId, id));
		const far = new Date('2040-06-01T09:00:00.000Z');
		await db.insert(busyIntervals).values({ id: newId(), userId: me, calendarId: cal.id, start: far, end: new Date(far.getTime() + 3600_000) });
		expect((await loadBusy([me], far.getTime() - DAY, far.getTime() + DAY)).get(me)!.map((b) => b.start)).toEqual([far.getTime()]);
	});

	test('an unchanged calendar is skipped; a changed one is re-read and only the difference written', async () => {
		const me = await fx.user();
		const id = await connection(me);
		fake.calendars = [{ externalId: 'home', name: 'Home', color: null, changes: 'version', version: 'v1' }];
		fake.events = [busy('home', yearsAhead(0.1)), busy('home', yearsAhead(1))];
		await syncConnection(id);
		const before = await rows(me);

		fake.reads = [];
		await db.update(calendarConnections).set({ nextSyncAt: null }).where(eq(calendarConnections.id, id));
		await syncConnection(id);
		expect(fake.reads).toEqual([]);

		// One event moved: the other row is left exactly as it was.
		fake.calendars[0].version = 'v2';
		fake.events = [fake.events[0], busy('home', yearsAhead(1.5))];
		await syncConnection(id);
		expect(fake.reads).toHaveLength(1);
		const after = await rows(me);
		expect(after).toHaveLength(2);
		expect(after[0].id).toBe(before[0].id);
		expect(after[1].start).toEqual(fake.events[1].start);
	});

	test('change-tracked calendars only rewrite the events that changed', async () => {
		const me = await fx.user();
		const id = await connection(me);
		fake.calendars = [{ externalId: 'work', name: 'Work', color: null, changes: 'cursor' }];
		fake.events = [busy('work', yearsAhead(0.2), 1, 'standup'), busy('work', yearsAhead(0.3), 1, 'standup'), busy('work', yearsAhead(2), 2, 'offsite')];
		await syncConnection(id);
		expect(fake.changeCalls).toEqual([]);
		const [cal] = await db.select().from(calendars).where(eq(calendars.connectionId, id));
		const cursor = cal.syncCursor!;
		expect(Number.isFinite(Date.parse(cursor))).toBe(true);

		// The offsite was cancelled; the standup didn't change, so it isn't in the answer.
		fake.reads = [];
		fake.changed = new Map([['offsite', []]]);
		await syncConnection(id);
		expect(fake.reads).toEqual([]);
		expect(fake.changeCalls).toHaveLength(1);
		expect(fake.changeCalls[0].since.toISOString()).toBe(cursor);
		expect((await rows(me)).map((r) => r.eventKey)).toEqual(['standup', 'standup']);

		// A cursor the provider no longer accepts: read everything again.
		fake.changed = null;
		fake.events = [busy('work', yearsAhead(0.2), 1, 'standup')];
		await syncConnection(id);
		expect(fake.reads).toHaveLength(1);
		expect(await rows(me)).toHaveLength(1);
	});

	test('calendars without change tracking re-read the near tier every sync and the rest daily', async () => {
		const me = await fx.user();
		const id = await connection(me);
		fake.calendars = [{ externalId: 'outlook', name: 'Outlook', color: null, changes: 'none' }];
		fake.events = [busy('outlook', yearsAhead(0.1)), busy('outlook', yearsAhead(2))];
		await syncConnection(id);
		expect(fake.reads).toHaveLength(1);
		const farAt = (await conn(id)).farSyncedAt;
		expect(farAt).not.toBeNull();

		fake.reads = [];
		fake.events = [busy('outlook', yearsAhead(0.1))];
		await syncConnection(id);
		expect(fake.reads).toHaveLength(1);
		expect(fake.reads[0].to.getTime() - Date.now()).toBeLessThan(184 * DAY);
		// The far event isn't re-read yet, so it's still stored.
		expect(await rows(me)).toHaveLength(2);

		// A day later the far tier is read again and the cancelled event goes.
		fake.reads = [];
		await db.update(calendarConnections).set({ farSyncedAt: new Date(Date.now() - 2 * DAY) }).where(eq(calendarConnections.id, id));
		await syncConnection(id);
		expect(fake.reads).toHaveLength(2);
		expect(await rows(me)).toHaveLength(1);
	});

	test('a group planning further ahead extends coverage, reading only the new part', async () => {
		const me = await fx.user();
		const id = await connection(me);
		fake.calendars = [{ externalId: 'home', name: 'Home', color: null, changes: 'version', version: 'v1' }];
		const sabbatical = busy('home', yearsAhead(5), 24 * 30);
		fake.events = [busy('home', yearsAhead(0.1)), sabbatical];
		const groupId = (await createGroup(me, { name: 'Soon', requirement: everyDay('2027-01-01', '2027-02-01') }, 'UTC')).id;
		await syncConnection(id);
		const covered = (await conn(id)).coveredUntil!;
		expect(await rows(me)).toHaveLength(1);

		fake.reads = [];
		const to = new Date(Date.now() + 6 * YEAR).toISOString().slice(0, 10);
		await updateGroup(groupId, me, { requirement: everyDay('2027-01-01', to) });
		expect((await conn(id)).nextSyncAt).toBeNull();

		await syncConnection(id);
		expect(fake.reads).toHaveLength(1);
		expect(fake.reads[0].from).toEqual(covered);
		expect((await conn(id)).coveredUntil!.getTime()).toBeGreaterThan(Date.parse(to));
		expect(await rows(me)).toHaveLength(2);

		// Already covered: nothing to hurry.
		await syncConnection(id);
		await refreshCoverage([me]);
		expect((await conn(id)).nextSyncAt).not.toBeNull();
	});
});
