import './setup';
import { afterAll, beforeAll, beforeEach, describe, expect, test } from 'bun:test';
import { eq } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { calendarConnections, calendars } from '#lib/server/db/schema';
import { encryptJson } from '#lib/server/crypto';
import { env } from '#lib/server/env';
import { newId } from '#lib/server/ids';
import { adapters, removeConnection, setCalendarBusy, syncConnection } from '#lib/server/calendars';
import { pushToken } from '#lib/server/calendars/push';
import { onGoogleNotification, onMicrosoftNotifications, pushTiming } from '#lib/server/calendars/webhooks';
import type { CalendarAdapter, RemoteBusy, RemoteCalendar } from '#lib/server/calendars/types';
import { POST as microsoftWebhook } from '../../src/routes/api/v1/calendars/push/microsoft/+server';
import { fixture } from './fixtures';

const fx = fixture('calendar-push');
const HOUR = 60 * 60 * 1000;

/** A provider the test controls, recording channels and reads. */
const fake = {
	calendars: [] as RemoteCalendar[],
	events: [] as RemoteBusy[],
	watchable: true,
	/** Ids `changeIsFar` says are far off. */
	farIds: new Set<string>(),
	ttlMs: 7 * 24 * HOUR,
	watched: [] as { calendar: string; id: string; address: string; token: string }[],
	stopped: [] as string[],
	reads: [] as { from: Date; to: Date }[],
	farChecks: [] as (string | null)[]
};
let nextChannel = 0;
const adapter: CalendarAdapter<object> = {
	listCalendars: async () => fake.calendars.map((c) => ({ ...c })),
	busy: async (_c, cals, from, to) => {
		fake.reads.push({ from, to });
		return fake.events.filter((e) => cals.some((c) => c.externalId === e.calendarExternalId) && e.start < to && e.end > from);
	},
	watch: async (_c, cal, req) => {
		if (!fake.watchable) return null;
		const id = `chan-${++nextChannel}`;
		fake.watched.push({ calendar: cal.externalId, id, address: req.address, token: req.token });
		return { id, resourceId: `res-${id}`, expiresAt: new Date(Date.now() + fake.ttlMs) };
	},
	unwatch: async (_c, channel) => {
		fake.stopped.push(channel.id);
	},
	changeIsFar: async (_c, eventId) => {
		fake.farChecks.push(eventId);
		return eventId === null || fake.farIds.has(eventId);
	}
};

/** `env` is read-only to the app; the tests point push at a fake address. */
const mutableEnv = env as { calendarPushUrl: string | null };
const real = adapters.caldav;
const realPushUrl = env.calendarPushUrl;
const realDebounce = pushTiming.debounceMs;
beforeAll(() => {
	adapters.caldav = adapter as typeof real;
	mutableEnv.calendarPushUrl = 'https://push.test.invalid';
	pushTiming.debounceMs = 10;
});
afterAll(async () => {
	adapters.caldav = real;
	mutableEnv.calendarPushUrl = realPushUrl;
	pushTiming.debounceMs = realDebounce;
	await fx.cleanup();
});
beforeEach(() => {
	fake.calendars = [
		{ externalId: 'home', name: 'Home', color: null, changes: 'none' },
		{ externalId: 'holidays', name: 'Holidays', color: null, changes: 'none' }
	];
	fake.events = [];
	fake.watchable = true;
	fake.farIds = new Set();
	fake.ttlMs = 7 * 24 * HOUR;
	fake.watched = [];
	fake.stopped = [];
	fake.reads = [];
	fake.farChecks = [];
});

async function connection(userId: string): Promise<string> {
	const id = newId();
	await db.insert(calendarConnections).values({
		id,
		userId,
		provider: 'caldav',
		label: `Push ${id}`,
		credentials: encryptJson({ serverUrl: 'https://dav.invalid', username: 'u', password: 'p' }, `calendar:${id}`)
	});
	return id;
}
const conn = async (id: string) => (await db.query.calendarConnections.findFirst({ where: eq(calendarConnections.id, id) }))!;
const cals = async (connectionId: string) => db.select().from(calendars).where(eq(calendars.connectionId, connectionId));
const calendar = async (connectionId: string, externalId: string) => (await cals(connectionId)).find((c) => c.externalId === externalId)!;
/** Waits for the debounced sync a notification starts. */
const settle = () => new Promise((r) => setTimeout(r, 300));

describe('keeping channels', () => {
	test('busy calendars are watched and the connection is then polled rarely', async () => {
		const me = await fx.user();
		const id = await connection(me);
		await syncConnection(id);
		// "Holidays" starts not busy, so only Home is watched.
		expect(fake.watched.map((w) => w.calendar)).toEqual(['home']);
		const home = await calendar(id, 'home');
		expect(fake.watched[0]).toMatchObject({ address: 'https://push.test.invalid/api/v1/calendars/push/caldav', token: pushToken(home.id) });
		expect(home).toMatchObject({ pushId: 'chan-1', pushResourceId: 'res-chan-1' });
		expect((await conn(id)).nextSyncAt!.getTime() - Date.now()).toBeGreaterThan(5 * HOUR);

		// A live channel is left alone.
		await syncConnection(id);
		expect(fake.watched).toHaveLength(1);
	});

	test('a channel close to lapsing is replaced, new one first', async () => {
		const me = await fx.user();
		const id = await connection(me);
		fake.ttlMs = 2 * HOUR;
		await syncConnection(id);
		fake.ttlMs = 7 * 24 * HOUR;
		await syncConnection(id);
		expect(fake.watched.map((w) => w.id)).toHaveLength(2);
		expect(fake.stopped).toEqual([fake.watched[0].id]);
		expect((await calendar(id, 'home')).pushId).toBe(fake.watched[1].id);
	});

	test('calendars that can’t be watched keep the connection on regular polling', async () => {
		const me = await fx.user();
		const id = await connection(me);
		fake.watchable = false;
		await syncConnection(id);
		expect((await calendar(id, 'home')).pushId).toBeNull();
		expect((await conn(id)).nextSyncAt!.getTime() - Date.now()).toBeLessThan(HOUR);
	});

	test('turning a calendar off stops its channel; removing the connection stops the rest', async () => {
		const me = await fx.user();
		const id = await connection(me);
		await syncConnection(id);
		const home = await calendar(id, 'home');
		const holidays = await calendar(id, 'holidays');

		await setCalendarBusy(me, id, holidays.id, true);
		expect((await conn(id)).nextSyncAt).toBeNull();
		await syncConnection(id);
		const holidayChannel = (await calendar(id, 'holidays')).pushId!;

		await setCalendarBusy(me, id, home.id, false);
		await syncConnection(id);
		expect(fake.stopped).toEqual([home.pushId!]);
		expect((await calendar(id, 'home')).pushId).toBeNull();

		await removeConnection(me, id);
		expect(fake.stopped).toEqual([home.pushId!, holidayChannel]);
	});
});

describe('notifications', () => {
	test('a Google notification with the right token syncs the connection; others are ignored', async () => {
		const me = await fx.user();
		const id = await connection(me);
		await syncConnection(id);
		const home = await calendar(id, 'home');
		const headers = (token: string, state = 'exists') =>
			new Headers({ 'x-goog-channel-id': home.pushId!, 'x-goog-channel-token': token, 'x-goog-resource-state': state });

		fake.reads = [];
		await onGoogleNotification(headers('forged'));
		await onGoogleNotification(headers(pushToken(home.id), 'sync'));
		await settle();
		expect(fake.reads).toEqual([]);

		fake.events = [{ calendarExternalId: 'home', start: new Date(Date.now() + 24 * HOUR), end: new Date(Date.now() + 25 * HOUR) }];
		// A burst of notifications: one sync.
		for (let i = 0; i < 3; i++) await onGoogleNotification(headers(pushToken(home.id)));
		await settle();
		expect(fake.reads).toHaveLength(1);
		expect((await conn(id)).lastSyncedAt!.getTime()).toBeGreaterThan(Date.now() - 5000);
	});

	test('Outlook changes re-read the far tier only when one may be far off', async () => {
		const me = await fx.user();
		const id = await connection(me);
		await syncConnection(id);
		const home = await calendar(id, 'home');
		const note = (eventId: string, changeType = 'updated') => ({
			subscriptionId: home.pushId!,
			clientState: pushToken(home.id),
			changeType,
			resourceData: { id: eventId }
		});

		fake.reads = [];
		await onMicrosoftNotifications({ value: [note('dentist')] });
		await settle();
		expect(fake.farChecks).toEqual(['dentist']);
		expect(fake.reads).toHaveLength(1); // the near tier only

		fake.reads = [];
		fake.farIds.add('wedding');
		await onMicrosoftNotifications({ value: [note('dentist'), note('wedding')] });
		await settle();
		expect(fake.reads).toHaveLength(2); // near and far

		fake.reads = [];
		fake.farChecks = [];
		await onMicrosoftNotifications({ value: [note('dentist', 'deleted')] });
		await settle();
		expect(fake.farChecks).toEqual([null]);
		expect(fake.reads).toHaveLength(2);

		// Someone else's subscription id, or a forged client state: ignored.
		fake.reads = [];
		await onMicrosoftNotifications({ value: [{ ...note('x'), clientState: 'forged' }, { ...note('x'), subscriptionId: 'unknown' }] });
		await settle();
		expect(fake.reads).toEqual([]);
	});

	test('the Microsoft endpoint echoes the validation token as plain text', async () => {
		const url = new URL('https://push.test.invalid/api/v1/calendars/push/microsoft?validationToken=Validation%3A+abc');
		const res = await microsoftWebhook({ request: new Request(url, { method: 'POST' }), url } as Parameters<typeof microsoftWebhook>[0]);
		expect(res.status).toBe(200);
		expect(res.headers.get('content-type')).toBe('text/plain');
		expect(await res.text()).toBe('Validation: abc');
	});
});
