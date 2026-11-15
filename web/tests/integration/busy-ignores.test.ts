import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { syncDevice, type DeviceSyncInput } from '#lib/server/calendars';
import { busyDuring, ignoreBusy, loadBusy, myBusy, unignoreBusy } from '#lib/server/busy';
import { apiError, fixture } from './fixtures';

const fx = fixture('busy-ignores');
afterAll(() => fx.cleanup());

const range = [Date.parse('2031-07-01T00:00:00Z'), Date.parse('2031-08-01T00:00:00Z')] as const;

describe('marking calendar events not busy', () => {
	test('one occurrence, then the whole series; undo; only your own calendars', async () => {
		const [me, someoneElse] = await fx.users(2);
		const input: DeviceSyncInput = {
			deviceId: 'phone-ignores',
			deviceName: 'Phone',
			rangeStart: '2031-07-01T00:00:00.000Z',
			rangeEnd: '2031-08-01T00:00:00.000Z',
			calendars: [{ externalId: 'home', name: 'Home', color: null, busy: true }],
			busy: [
				{ calendarExternalId: 'home', eventKey: 'choir', start: '2031-07-01T18:00:00.000Z', end: '2031-07-01T20:00:00.000Z' },
				{ calendarExternalId: 'home', eventKey: 'choir', start: '2031-07-08T18:00:00.000Z', end: '2031-07-08T20:00:00.000Z' },
				{ calendarExternalId: 'home', eventKey: 'dentist', start: '2031-07-03T09:00:00.000Z', end: '2031-07-03T10:00:00.000Z' }
			]
		};
		await syncDevice(me, input);
		const mine = await myBusy(me, ...range);
		const choir = mine.find((b) => b.eventKey === 'choir')!;
		expect(choir.recurring).toBe(true);
		expect(mine.find((b) => b.eventKey === 'dentist')!.recurring).toBe(false);

		// Someone else can't touch your calendar.
		expect((await apiError(ignoreBusy(someoneElse, { calendarId: choir.calendarId!, eventKey: 'choir', start: choir.start, end: choir.end, series: false }))).code).toBe('not_found');

		// One occurrence: that evening is free, the next week's still busy.
		const one = await ignoreBusy(me, { calendarId: choir.calendarId!, eventKey: 'choir', start: choir.start, end: choir.end, series: false });
		expect(await busyDuring([me], Date.parse('2031-07-01T18:30:00Z'), Date.parse('2031-07-01T19:00:00Z'))).toEqual([]);
		expect(await busyDuring([me], Date.parse('2031-07-08T18:30:00Z'), Date.parse('2031-07-08T19:00:00Z'))).toEqual([me]);
		// Still listed for you, flagged, so you can undo it.
		expect((await myBusy(me, ...range)).filter((b) => b.ignoreId === one.id)).toHaveLength(1);

		// The whole series.
		const series = await ignoreBusy(me, { calendarId: choir.calendarId!, eventKey: 'choir', start: choir.start, end: choir.end, series: true });
		const busyNow = (await loadBusy([me], ...range)).get(me)!;
		expect(busyNow.map((b) => b.eventKey)).toEqual(['dentist']);

		await unignoreBusy(me, series.id);
		await unignoreBusy(me, one.id);
		expect((await loadBusy([me], ...range)).get(me)!).toHaveLength(3);
		expect((await apiError(unignoreBusy(me, one.id))).code).toBe('not_found');
	});
});
