import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { createGroup } from '#lib/server/groups';
import { sendText } from '#lib/server/chat';
import { markChatRead } from '#lib/server/notifications';
import {
	catchUpBody,
	getNotificationSettings,
	inQuietHours,
	muteGroup,
	pushRecipients,
	sendQuietHoursCatchUps,
	unmuteGroup,
	updateNotificationSettings
} from '#lib/server/notification-settings';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('notif');
afterAll(() => fx.cleanup());

describe('quiet hours', () => {
	test('wrap past midnight, in the user’s time zone', () => {
		const q = { from: '22:00', to: '07:00' };
		expect(inQuietHours(q, 'UTC', Date.parse('2032-01-10T23:30:00Z'))).toBe(true);
		expect(inQuietHours(q, 'UTC', Date.parse('2032-01-10T06:59:00Z'))).toBe(true);
		expect(inQuietHours(q, 'UTC', Date.parse('2032-01-10T07:00:00Z'))).toBe(false);
		// 21:30 UTC is 22:30 in Paris (winter).
		expect(inQuietHours(q, 'Europe/Paris', Date.parse('2032-01-10T21:30:00Z'))).toBe(true);
		expect(inQuietHours({ from: '13:00', to: '14:00' }, 'UTC', Date.parse('2032-01-10T13:30:00Z'))).toBe(true);
		expect(inQuietHours(null, 'UTC', Date.now())).toBe(false);
	});
});

describe('who gets a push', () => {
	test('categories turned off, muted groups and quiet hours leave people out; invites always come', async () => {
		const [a, b, c, d] = await fx.users(4);
		const groupId = (await createGroup(a, { name: 'Muted', requirement: everyDay() }, 'UTC')).id;
		for (const id of [b, c, d]) await fx.join(groupId, id);
		const noon = Date.parse('2032-01-10T12:00:00Z');

		await updateNotificationSettings(b, { off: ['chat'] });
		await muteGroup(c, groupId, null);
		await updateNotificationSettings(d, { quietHours: { from: '11:00', to: '13:00' } });

		expect(await pushRecipients([a, b, c, d], 'message', groupId, noon)).toEqual([a]);
		expect(await pushRecipients([a, b, c, d], 'proposal.created', groupId, noon)).toEqual([a, b]);
		expect(await pushRecipients([a, b, c, d], 'invite.received', groupId, noon)).toEqual([a, b, c, d]);

		// A mute that has run out no longer applies; unmuting removes it.
		await muteGroup(c, groupId, '2032-01-10T11:00:00.000Z');
		expect(await pushRecipients([c], 'message', groupId, noon)).toEqual([c]);
		expect((await getNotificationSettings(c, noon)).mutes).toEqual([]);
		await muteGroup(c, groupId, null);
		expect((await getNotificationSettings(c)).mutes).toEqual([{ groupId, until: null }]);
		expect((await unmuteGroup(c, groupId)).mutes).toEqual([]);
	});

	test('only members can mute a group', async () => {
		const [owner, outsider] = await fx.users(2);
		const groupId = (await createGroup(owner, { name: 'Private', requirement: everyDay() }, 'UTC')).id;
		expect((await apiError(muteGroup(outsider, groupId, null))).code).toBe('not_found');
	});
});

describe('catching up after quiet hours', () => {
	const hhmm = (t: number) => new Date(t).toISOString().slice(11, 16);
	const HOUR = 60 * 60 * 1000;

	test('the summary line', () => {
		expect(catchUpBody(1, [])).toBe('1 update');
		expect(catchUpBody(4, ['Lake District crew'])).toBe('4 updates in Lake District crew');
		expect(catchUpBody(5, ['Lake District crew', 'Pub quiz'])).toBe('5 updates in Lake District crew and Pub quiz');
		expect(catchUpBody(6, ['A', 'B', 'C'])).toBe('6 updates in A, B and 1 more');
	});

	test('a push held back by quiet hours is owed once they end, only with catch-up on, and only once', async () => {
		const [sender, quiet, plain] = await fx.users(3);
		const groupId = (await createGroup(sender, { name: 'Late night', requirement: everyDay() }, 'UTC')).id;
		await fx.join(groupId, quiet);
		await fx.join(groupId, plain);
		const now = Date.now();
		// Quiet hours around now, in UTC (the fixture users' time zone).
		const quietHours = { from: hhmm(now - HOUR), to: hhmm(now + HOUR) };
		await updateNotificationSettings(quiet, { quietHours: { ...quietHours, catchUp: true } });
		await updateNotificationSettings(plain, { quietHours });
		expect((await getNotificationSettings(quiet)).quietHours).toEqual({ ...quietHours, catchUp: true });

		expect(await pushRecipients([quiet, plain], 'message', groupId, now - 1000)).toEqual([]);
		await sendText(groupId, sender, 'Still up?');
		await sendText(groupId, sender, 'Anyone?');

		// Still quiet: nothing yet.
		expect(await sendQuietHoursCatchUps(now)).not.toContain(quiet);
		// Afterwards: one for the person with catch-up on, then nothing more.
		const after = now + 2 * HOUR;
		const sent = await sendQuietHoursCatchUps(after);
		expect(sent).toContain(quiet);
		expect(sent).not.toContain(plain);
		expect(await sendQuietHoursCatchUps(after)).not.toContain(quiet);
	});

	test('nothing is sent when it was all read meanwhile, or catch-up was turned off', async () => {
		const [sender, reader, switcher] = await fx.users(3);
		const groupId = (await createGroup(sender, { name: 'Read it', requirement: everyDay() }, 'UTC')).id;
		await fx.join(groupId, reader);
		await fx.join(groupId, switcher);
		const now = Date.now();
		const quietHours = { from: hhmm(now - HOUR), to: hhmm(now + HOUR), catchUp: true };
		await updateNotificationSettings(reader, { quietHours });
		await updateNotificationSettings(switcher, { quietHours });
		await pushRecipients([reader, switcher], 'message', groupId, now - 1000);
		await sendText(groupId, sender, 'Morning');
		await markChatRead(reader, groupId);
		await updateNotificationSettings(switcher, { quietHours: { from: quietHours.from, to: quietHours.to, catchUp: false } });
		const sent = await sendQuietHoursCatchUps(now + 2 * HOUR);
		expect(sent).not.toContain(reader);
		expect(sent).not.toContain(switcher);
	});
});

test('changing only the times keeps catching up as it was', async () => {
	const [u] = await fx.users(1);
	await updateNotificationSettings(u, { quietHours: { from: '22:00', to: '07:00', catchUp: true } });
	expect((await updateNotificationSettings(u, { quietHours: { from: '23:00', to: '07:00' } })).quietHours).toEqual({
		from: '23:00',
		to: '07:00',
		catchUp: true
	});
});
