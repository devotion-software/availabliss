import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { createGroup } from '#lib/server/groups';
import {
	getNotificationSettings,
	inQuietHours,
	muteGroup,
	pushRecipients,
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
