import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import { autoArchiveGroups } from '#lib/server/auto-archive';
import { createGroup, getGroup, updateGroup } from '#lib/server/groups';
import { createEvent } from '#lib/server/events';
import { cancelProposal, createProposal } from '#lib/server/proposals';
import { getMe, updateMe } from '#lib/server/users';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('autoarchive');
afterAll(() => fx.cleanup());

const DAY = 24 * 60 * 60 * 1000;
// Everything happens in the past (the job runs "as of" a day in it), so unarchiving sees those events as passed.
const base = Math.floor(Date.now() / DAY) * DAY - 40 * DAY + 18 * 60 * 60 * 1000;
const at = (d: number, h = 0) => new Date(base + d * DAY + h * 60 * 60 * 1000);
const slot = (d: number) => ({ start: at(d).toISOString(), end: at(d, 3).toISOString(), allDay: false, title: null, location: null, note: null });
const event = (d: number) => ({ ...slot(d), title: 'Night out' });

let owner: string, member: string, groupId: string, manualId: string;
const archived = async (id = groupId) => (await getGroup(id, member)).archived;
/** Runs the job as of `when`, and says whether it archived the group. */
const run = async (when: Date, id = groupId) => (await autoArchiveGroups(when)).includes(id);

beforeAll(async () => {
	owner = await fx.user();
	member = await fx.user();
	groupId = (await createGroup(owner, { name: 'Birthday drinks', requirement: everyDay() }, 'UTC')).id;
	manualId = (await createGroup(owner, { name: 'Keeps going', requirement: everyDay() }, 'UTC')).id;
	await fx.join(groupId, member);
	await fx.join(manualId, member);
});

describe('auto-archive', () => {
	test('off by default: a passed event leaves the group alone', async () => {
		expect((await getGroup(manualId, owner)).autoArchive).toBe(false);
		await createEvent(manualId, owner, event(1));
		expect(await run(at(2), manualId)).toBe(false);
		expect(await archived(manualId)).toBe(false);
	});

	test('admins turn it on; it archives once the latest event has passed', async () => {
		expect((await apiError(updateGroup(groupId, member, { autoArchive: true }))).code).toBe('forbidden');
		expect((await updateGroup(groupId, owner, { autoArchive: true })).autoArchive).toBe(true);
		// No event yet: nothing to archive after.
		expect(await run(at(0))).toBe(false);
		await createEvent(groupId, owner, event(1));
		// Before it, and while it's on: still planned.
		expect(await run(at(0))).toBe(false);
		expect(await run(at(1, 1))).toBe(false);
		expect(await run(at(2))).toBe(true);
		expect(await archived()).toBe(true);
	});

	test('unarchived by hand, it stays out until a newer event has passed', async () => {
		await updateGroup(groupId, owner, { archived: false });
		expect(await run(at(3))).toBe(false);
		expect(await archived()).toBe(false);

		await createEvent(groupId, member, event(5));
		// An open proposal for a time still ahead keeps it out; a cancelled one doesn't.
		const p = await createProposal(groupId, member, slot(8));
		expect(await run(at(6))).toBe(false);
		await cancelProposal(p.id, member);
		expect(await run(at(6))).toBe(true);
	});

	test('a new proposal brings an archived group back', async () => {
		expect(await archived()).toBe(true);
		await createProposal(groupId, member, slot(10));
		expect(await archived()).toBe(false);
		// Archiving by hand isn't undone by the job's rule, but new plans still bring it back.
		await updateGroup(groupId, owner, { archived: true });
		await createEvent(groupId, owner, event(12));
		expect(await archived()).toBe(false);
	});
});

describe('auto-archive for groups you create (your setting)', () => {
	test('off by default; on, your new groups start with it on, and each can still turn it off', async () => {
		const creator = await fx.user();
		expect((await createGroup(creator, { name: 'Before', requirement: everyDay() }, 'UTC')).autoArchive).toBe(false);
		await updateMe(creator, { autoArchiveNewGroups: true });
		const after = await createGroup(creator, { name: 'After', requirement: everyDay() }, 'UTC');
		expect(after.autoArchive).toBe(true);
		expect((await updateGroup(after.id, creator, { autoArchive: false })).autoArchive).toBe(false);
		expect((await getMe(creator)).autoArchiveNewGroups).toBe(true);
	});
});
