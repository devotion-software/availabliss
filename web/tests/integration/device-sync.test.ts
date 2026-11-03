import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { createGroup } from '#lib/server/groups';
import { eq } from 'drizzle-orm';
import { db, isDuplicateKey } from '#lib/server/db';
import { calendarConnections } from '#lib/server/db/schema';
import { newId } from '#lib/server/ids';
import { syncConnection, syncDevice, syncStaleConnections, type DeviceSyncInput } from '#lib/server/calendars';
import { loadBusy } from '#lib/server/busy';
import { subscribe } from '#lib/server/realtime';
import type { LiveEvent } from '#lib/types';
import { everyDay, fixture } from './fixtures';

const fx = fixture('device-sync');
afterAll(() => fx.cleanup());

const settle = () => new Promise((r) => setTimeout(r, 50));

describe('syncDevice', () => {
	test('only tells the group when busy time or calendars changed', async () => {
		const [me, friend] = await fx.users(2);
		const groupId = (await createGroup(me, { name: 'Walkers', requirement: everyDay() }, 'UTC')).id;
		await fx.join(groupId, friend);

		const seen: LiveEvent[] = [];
		const off = subscribe(friend, (e) => e.type === 'availability.changed' && seen.push(e));
		try {
			const input: DeviceSyncInput = {
				deviceId: 'phone-1',
				deviceName: 'Phone',
				rangeStart: '2031-05-01T00:00:00.000Z',
				rangeEnd: '2031-06-01T00:00:00.000Z',
				calendars: [{ externalId: 'home', name: 'Home', color: null, busy: true }],
				busy: [{ calendarExternalId: 'home', start: '2031-05-10T09:00:00.500Z', end: '2031-05-10T10:00:00.000Z' }]
			};
			await syncDevice(me, input);
			await settle();
			expect(seen.length).toBe(1);

			// The same upload again (the apps send one on every foreground): nothing to say.
			await syncDevice(me, structuredClone(input));
			await settle();
			expect(seen.length).toBe(1);

			// A new busy interval.
			const busier = { ...input, busy: [...input.busy, { calendarExternalId: 'home', start: '2031-05-11T09:00:00.000Z', end: '2031-05-11T10:00:00.000Z' }] };
			await syncDevice(me, busier);
			await settle();
			expect(seen.length).toBe(2);
			const stored = await loadBusy([me], Date.parse(input.rangeStart), Date.parse(input.rangeEnd));
			expect(stored.get(me)!.map((b) => new Date(b.start).toISOString()).sort()).toEqual(['2031-05-10T09:00:00.500Z', '2031-05-11T09:00:00.000Z']);

			// A renamed calendar counts as a change; then unchanged again.
			await syncDevice(me, { ...busier, calendars: [{ ...input.calendars[0], name: 'Family' }] });
			await settle();
			expect(seen.length).toBe(3);
			await syncDevice(me, { ...busier, calendars: [{ ...input.calendars[0], name: 'Family' }] });
			await settle();
			expect(seen.length).toBe(3);
		} finally {
			off();
		}
	});
});

describe('connection uniqueness', () => {
	const input = (deviceId: string): DeviceSyncInput => ({
		deviceId,
		deviceName: 'iPhone',
		rangeStart: '2031-05-01T00:00:00.000Z',
		rangeEnd: '2031-06-01T00:00:00.000Z',
		calendars: [{ externalId: 'home', name: 'Home', color: null, busy: true }],
		busy: []
	});

	test('a fresh device uploading twice at once makes one connection', async () => {
		const me = await fx.user();
		const [a, b] = await Promise.all([syncDevice(me, input('race-1')), syncDevice(me, input('race-1'))]);
		expect(a.id).toBe(b.id);
		const rows = await db.select().from(calendarConnections).where(eq(calendarConnections.userId, me));
		expect(rows).toHaveLength(1);
	});

	test('two devices may share a name, but a server account is one connection', async () => {
		const me = await fx.user();
		await syncDevice(me, input('phone-a'));
		await syncDevice(me, input('phone-b'));
		const caldav = { userId: me, provider: 'caldav' as const, label: 'iCloud · me@example.com' };
		await db.insert(calendarConnections).values({ id: newId(), ...caldav });
		const dup = await db.insert(calendarConnections).values({ id: newId(), ...caldav }).then(() => null, (err) => err);
		expect(isDuplicateKey(dup)).toBe(true);
		expect(await db.select().from(calendarConnections).where(eq(calendarConnections.userId, me))).toHaveLength(3);
	});
});

describe('background sync', () => {
	test('a failing connection is stamped and backs off instead of coming straight back', async () => {
		const me = await fx.user();
		const id = newId();
		// Credentials that can't be decrypted: the sync fails before any network call.
		await db.insert(calendarConnections).values({ id, userId: me, provider: 'caldav', label: 'Broken', credentials: 'not-encrypted' });
		const row = async () => (await db.query.calendarConnections.findFirst({ where: eq(calendarConnections.id, id) }))!;

		const t0 = Date.now();
		await syncConnection(id);
		let r = await row();
		expect(r).toMatchObject({ status: 'error', failures: 1, lastSyncedAt: null });
		expect(r.nextSyncAt!.getTime() - t0).toBeGreaterThanOrEqual(30 * 60 * 1000 - 1000);

		await syncConnection(id);
		r = await row();
		expect(r.failures).toBe(2);
		expect(r.nextSyncAt!.getTime() - t0).toBeGreaterThanOrEqual(60 * 60 * 1000 - 1000);
	});

	test('a run past its deadline starts nothing', async () => {
		expect(await syncStaleConnections(0)).toBe(0);
	});
});
