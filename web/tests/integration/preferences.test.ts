import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { createGroup } from '#lib/server/groups';
import { groupAvailability } from '#lib/server/availability/service';
import { addPreference, listPreferences, removePreference, updatePreference } from '#lib/server/preferences';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('preferences');
afterAll(() => fx.cleanup());

describe('group preferences', () => {
	test('mark the days that fit, never block, and only you can remove yours', async () => {
		const [owner, member, outsider] = await fx.users(3);
		const groupId = (await createGroup(owner, { name: 'Prefs', requirement: everyDay() }, 'UTC')).id;
		await fx.join(groupId, member);

		const before = await groupAvailability(groupId, owner, '2031-03-03', '2031-03-09');
		// Mon 3 Mar 2031 is a Monday: weekends are Sat 8 and Sun 9.
		const weekends = await addPreference(groupId, member, { kind: 'weekly', days: [5, 6], window: null });
		expect(weekends.label).toBe('Weekends');
		const wednesday = await addPreference(groupId, owner, { kind: 'dates', from: '2031-03-05', to: '2031-03-05' });

		const after = await groupAvailability(groupId, owner, '2031-03-03', '2031-03-09');
		expect(after.days.map((d) => d.status)).toEqual(before.days.map((d) => d.status));
		const by = Object.fromEntries(after.days.map((d) => [d.date, d.preferredBy]));
		expect(by['2031-03-08']).toEqual([member]);
		expect(by['2031-03-09']).toEqual([member]);
		expect(by['2031-03-05']).toEqual([owner]);
		expect(by['2031-03-04']).toEqual([]);
		expect(after.slots.find((s) => s.start.startsWith('2031-03-08'))?.preferredBy).toEqual([member]);

		expect((await listPreferences(groupId, owner)).map((p) => p.id)).toEqual([weekends.id, wednesday.id]);
		expect((await apiError(listPreferences(groupId, outsider))).code).toBe('not_found');
		expect((await apiError(addPreference(groupId, member, { kind: 'dates', from: '2031-03-09', to: '2031-03-01' }))).code).toBe('validation_error');
		expect((await apiError(removePreference(groupId, owner, weekends.id))).code).toBe('not_found');
		await removePreference(groupId, member, weekends.id);
		expect((await listPreferences(groupId, member)).map((p) => p.id)).toEqual([wednesday.id]);
	});

	test('change one in place: same id and place in the list, kind can change, only your own', async () => {
		const [owner, member] = await fx.users(2);
		const groupId = (await createGroup(owner, { name: 'Edit prefs', requirement: everyDay() }, 'UTC')).id;
		await fx.join(groupId, member);
		const first = await addPreference(groupId, member, { kind: 'weekly', days: [5, 6], window: null });
		const second = await addPreference(groupId, member, { kind: 'dates', from: '2031-03-05', to: '2031-03-05' });

		const evenings = await updatePreference(groupId, member, first.id, { kind: 'weekly', days: [4], window: { from: '18:00', to: '23:00' } });
		expect(evenings).toMatchObject({ id: first.id, kind: 'weekly', days: [4], window: { from: '18:00', to: '23:00' } });
		const dates = await updatePreference(groupId, member, second.id, { kind: 'weekly', days: [0] });
		expect(dates).toMatchObject({ id: second.id, kind: 'weekly', from: null, to: null, days: [0] });
		expect((await listPreferences(groupId, member)).map((p) => p.id)).toEqual([first.id, second.id]);

		expect((await apiError(updatePreference(groupId, owner, first.id, { kind: 'weekly', days: [1] }))).code).toBe('not_found');
		expect((await apiError(updatePreference(groupId, member, first.id, { kind: 'dates', from: '2031-03-09', to: '2031-03-01' }))).code).toBe(
			'validation_error'
		);
	});
});
