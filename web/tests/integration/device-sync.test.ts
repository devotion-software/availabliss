import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { createGroup } from '#lib/server/groups';
import { syncDevice, type DeviceSyncInput } from '#lib/server/calendars';
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
