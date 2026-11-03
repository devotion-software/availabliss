import './setup';
import { afterAll, beforeAll, describe, expect, test } from 'bun:test';
import { and, eq } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { events, messages, notifications } from '#lib/server/db/schema';
import { createGroup, removeMember } from '#lib/server/groups';
import {
	awaitingReply,
	cancelProposal,
	createProposal,
	getProposal,
	listProposals,
	onMemberJoined,
	onMemberLeft,
	override,
	respond,
	type ProposalInput
} from '#lib/server/proposals';
import { createBlock } from '#lib/server/blocks';
import { deleteAccount } from '#lib/server/users';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('proposals');
afterAll(() => fx.cleanup());

const slot = (day: number, overrides: Partial<ProposalInput> = {}): ProposalInput => ({
	start: `2031-03-${String(day).padStart(2, '0')}T18:00:00.000Z`,
	end: `2031-03-${String(day).padStart(2, '0')}T21:00:00.000Z`,
	allDay: false,
	title: null,
	location: null,
	note: null,
	...overrides
});

/** A group of owner + admin + member (+ an outsider) with an every-day timing. */
async function setup() {
	const [owner, admin, member, outsider] = await fx.users(4);
	const groupId = (await createGroup(owner, { name: 'Dinner club', requirement: everyDay() }, 'UTC')).id;
	await fx.join(groupId, admin, 'admin');
	await fx.join(groupId, member);
	return { owner, admin, member, outsider, groupId };
}

describe('createProposal', () => {
	let g: Awaited<ReturnType<typeof setup>>;
	beforeAll(async () => {
		g = await setup();
	});

	test('the proposer accepts automatically; everyone else is pending and notified', async () => {
		const p = await createProposal(g.groupId, g.member, slot(4, { title: 'Pizza', location: 'Luigi’s', note: 'Bring cash' }));
		expect(p).toMatchObject({ groupId: g.groupId, status: 'open', createdBy: g.member, title: 'Pizza', location: 'Luigi’s', note: 'Bring cash', eventId: null, allDay: false });
		expect(p.start).toBe('2031-03-04T18:00:00.000Z');
		const byUser = Object.fromEntries(p.responses.map((r) => [r.userId, r.response]));
		expect(byUser).toEqual({ [g.owner]: 'pending', [g.admin]: 'pending', [g.member]: 'accepted' });
		expect(p.busyMemberIds).toEqual([]);

		const notified = await db.select().from(notifications).where(and(eq(notifications.groupId, g.groupId), eq(notifications.kind, 'proposal.created')));
		expect(notified.map((n) => n.userId).sort()).toEqual([g.owner, g.admin].sort());
		expect(notified[0].data).toMatchObject({ proposalId: p.id, kind: 'proposal.created', groupId: g.groupId });

		const chat = await db.select().from(messages).where(eq(messages.groupId, g.groupId));
		expect(chat.some((m) => m.kind === 'proposal' && m.proposalId === p.id)).toBe(true);

		expect(await awaitingReply(g.groupId, g.owner)).toBe(p.id);
		expect(await awaitingReply(g.groupId, g.member)).toBeNull();
	});

	test('non-members cannot propose or read proposals', async () => {
		expect((await apiError(createProposal(g.groupId, g.outsider, slot(5)))).code).toBe('not_found');
		const p = await createProposal(g.groupId, g.owner, slot(5));
		expect((await apiError(getProposal(p.id, g.outsider))).code).toBe('not_found');
		expect((await apiError(listProposals(g.groupId, g.outsider))).code).toBe('not_found');
		expect((await apiError(getProposal('missing', g.owner))).code).toBe('not_found');
	});

	test('a single-member group confirms immediately', async () => {
		const solo = await fx.user();
		const groupId = (await createGroup(solo, { name: 'Just me', requirement: everyDay() }, 'UTC')).id;
		const p = await createProposal(groupId, solo, slot(6));
		expect(p.status).toBe('confirmed');
		expect(p.eventId).toBeTruthy();
	});
});

describe('responding', () => {
	test('everyone accepting confirms the proposal and creates the event', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(7, { location: 'The park' }));

		const afterAdmin = await respond(p.id, g.admin, 'accepted');
		expect(afterAdmin.status).toBe('open');

		const confirmed = await respond(p.id, g.member, 'accepted');
		expect(confirmed.status).toBe('confirmed');
		expect(confirmed.busyMemberIds).toEqual([]);
		const event = await db.query.events.findFirst({ where: eq(events.id, confirmed.eventId!) });
		expect(event).toMatchObject({ groupId: g.groupId, proposalId: p.id, title: 'Dinner club', location: 'The park', createdBy: g.owner });
		expect(event!.start.toISOString()).toBe(p.start);

		const kinds = (await db.select().from(notifications).where(eq(notifications.groupId, g.groupId))).map((n) => n.kind);
		expect(kinds.filter((k) => k === 'proposal.confirmed').length).toBe(3);
		const system = await db.select().from(messages).where(and(eq(messages.groupId, g.groupId), eq(messages.kind, 'system')));
		expect(system.length).toBe(1);

		// Confirmed proposals are closed to further changes.
		expect((await apiError(respond(p.id, g.member, 'declined'))).code).toBe('conflict');
		expect((await apiError(cancelProposal(p.id, g.owner))).code).toBe('conflict');
		expect(await listProposals(g.groupId, g.member, 'confirmed')).toEqual([expect.objectContaining({ id: p.id })]);
		expect(await listProposals(g.groupId, g.member, 'open')).toEqual([]);
	});

	test('the last two members accepting at once confirm it exactly once', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(11));
		const results = await Promise.all([respond(p.id, g.admin, 'accepted'), respond(p.id, g.member, 'accepted')]);
		expect(results.map((r) => r.status)).toEqual(['confirmed', 'confirmed']);

		const made = await db.select().from(events).where(eq(events.proposalId, p.id));
		expect(made.length).toBe(1);
		expect(results.every((r) => r.eventId === made[0].id)).toBe(true);
		const system = await db.select().from(messages).where(and(eq(messages.groupId, g.groupId), eq(messages.kind, 'system')));
		expect(system.length).toBe(1);
		const confirmedPushes = await db.select().from(notifications).where(and(eq(notifications.groupId, g.groupId), eq(notifications.kind, 'proposal.confirmed')));
		expect(confirmedPushes.length).toBe(3);
	});

	test('a cancel racing the last acceptance never leaves a cancelled proposal with an event', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(12));
		await respond(p.id, g.admin, 'accepted');
		await Promise.allSettled([respond(p.id, g.member, 'accepted'), cancelProposal(p.id, g.owner)]);
		const final = await getProposal(p.id, g.owner);
		const made = await db.select().from(events).where(eq(events.proposalId, p.id));
		expect(made.length).toBe(final.status === 'confirmed' ? 1 : 0);
	});

	test('a decline keeps it open; changing your mind can still confirm', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(8));
		await respond(p.id, g.admin, 'accepted');
		const declined = await respond(p.id, g.member, 'declined');
		expect(declined.status).toBe('open');
		expect(declined.responses.find((r) => r.userId === g.member)).toMatchObject({ response: 'declined', setBy: null });
		expect((await respond(p.id, g.member, 'accepted')).status).toBe('confirmed');
	});

	test('outsiders cannot respond', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(9));
		expect((await apiError(respond(p.id, g.outsider, 'accepted'))).code).toBe('not_found');
	});

	test('admins can override a response, which records who set it', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(10));
		expect((await apiError(override(p.id, g.member, g.admin, 'accepted'))).code).toBe('forbidden');
		expect((await apiError(override(p.id, g.admin, g.outsider, 'accepted'))).code).toBe('not_found');

		const o = await override(p.id, g.admin, g.member, 'declined');
		expect(o.responses.find((r) => r.userId === g.member)).toMatchObject({ response: 'declined', setBy: g.admin });

		await respond(p.id, g.admin, 'accepted');
		const confirmed = await override(p.id, g.owner, g.member, 'accepted');
		expect(confirmed.status).toBe('confirmed');
	});
});

describe('busy members', () => {
	test('busyMemberIds lists members with a clashing block, only while open', async () => {
		const g = await setup();
		await createBlock(g.member, { start: '2031-03-11T17:00:00Z', end: '2031-03-11T19:00:00Z', allDay: false, repeat: 'none', until: null, note: 'Gym' });
		// A weekly repeat that started weeks earlier still clashes.
		await createBlock(g.admin, { start: '2031-02-18T20:00:00Z', end: '2031-02-18T22:00:00Z', allDay: false, repeat: 'weekly', until: null, note: null });

		const p = await createProposal(g.groupId, g.owner, slot(11));
		expect(p.busyMemberIds.sort()).toEqual([g.admin, g.member].sort());

		const elsewhere = await createProposal(g.groupId, g.owner, slot(12));
		expect(elsewhere.busyMemberIds).toEqual([]);

		// Listed together (one busy load across both), each keeps its own clashes.
		const listed = Object.fromEntries((await listProposals(g.groupId, g.member, 'open')).map((x) => [x.id, x.busyMemberIds.sort()]));
		expect(listed).toEqual({ [p.id]: [g.admin, g.member].sort(), [elsewhere.id]: [] });

		await cancelProposal(p.id, g.owner);
		expect((await getProposal(p.id, g.member)).busyMemberIds).toEqual([]);
	});
});

describe('cancelProposal', () => {
	test('the proposer or an admin can cancel; other members cannot', async () => {
		const g = await setup();
		const byMember = await createProposal(g.groupId, g.member, slot(13));
		const byOwner = await createProposal(g.groupId, g.owner, slot(14));

		expect((await apiError(cancelProposal(byOwner.id, g.member))).code).toBe('forbidden');
		await cancelProposal(byMember.id, g.member);
		await cancelProposal(byOwner.id, g.admin);

		expect((await getProposal(byMember.id, g.owner)).status).toBe('cancelled');
		expect((await getProposal(byOwner.id, g.owner)).status).toBe('cancelled');
		expect((await apiError(respond(byOwner.id, g.member, 'accepted'))).message).toContain('cancelled');
		expect(await awaitingReply(g.groupId, g.admin)).toBeNull();
	});
});

describe('membership changes', () => {
	test('a new member starts pending on open proposals', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(15));
		const newcomer = await fx.user();
		await fx.join(g.groupId, newcomer);
		await onMemberJoined(g.groupId, newcomer);
		await onMemberJoined(g.groupId, newcomer); // idempotent
		const after = await getProposal(p.id, newcomer);
		expect(after.responses.find((r) => r.userId === newcomer)?.response).toBe('pending');
		expect(after.responses.length).toBe(4);
	});

	test('the last pending member deleting their account confirms the proposal', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(17));
		await respond(p.id, g.admin, 'accepted');
		await deleteAccount(g.member);
		expect((await getProposal(p.id, g.owner)).status).toBe('confirmed');
	});

	test('the last pending member leaving confirms the proposal', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(16));
		await respond(p.id, g.admin, 'accepted');
		await removeMember(g.groupId, g.member, g.member);
		await onMemberLeft(g.groupId, g.member);
		const after = await getProposal(p.id, g.owner);
		expect(after.status).toBe('confirmed');
		expect(after.responses.map((r) => r.userId).sort()).toEqual([g.owner, g.admin].sort());
	});
});
