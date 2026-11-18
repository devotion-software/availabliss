import './setup';
import { afterAll, beforeAll, describe, expect, spyOn, test } from 'bun:test';
import { and, eq } from 'drizzle-orm';
import { db } from '#lib/server/db';
import { events, messages, notifications } from '#lib/server/db/schema';
import { createGroup, removeMember } from '#lib/server/groups';
import { listNotifications } from '#lib/server/notifications';
import * as pushModule from '#lib/server/push';
import {
	awaitingReply,
	cancelProposal,
	confirmAnyway,
	createProposal,
	getProposal,
	keepPlan,
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
		const others = notified.filter((n) => n.userId !== g.member);
		expect(others.map((n) => n.userId).sort()).toEqual([g.owner, g.admin].sort());
		expect(others[0].data).toMatchObject({ proposalId: p.id, kind: 'proposal.created', groupId: g.groupId });
		expect(others[0].data.own).toBeUndefined();
		expect(others[0].body).toEndWith('Can you make it?');
		expect(others[0].readAt).toBeNull();

		const chat = await db.select().from(messages).where(eq(messages.groupId, g.groupId));
		expect(chat.some((m) => m.kind === 'proposal' && m.proposalId === p.id)).toBe(true);

		expect(await awaitingReply(g.groupId, g.owner)).toBe(p.id);
		expect(await awaitingReply(g.groupId, g.member)).toBeNull();
	});

	test('the proposer gets their own Activity item, already read and without a push', async () => {
		const push = spyOn(pushModule, 'sendPush');
		try {
			const p = await createProposal(g.groupId, g.admin, slot(6));
			const own = (
				await db
					.select()
					.from(notifications)
					.where(and(eq(notifications.userId, g.admin), eq(notifications.kind, 'proposal.created')))
			).filter((n) => n.data.proposalId === p.id);
			expect(own.length).toBe(1);
			expect(own[0]).toMatchObject({ groupId: g.groupId, title: 'Dinner club', body: 'You proposed Thu 6 Mar · 18:00–21:00.' });
			expect(own[0].data).toMatchObject({ proposalId: p.id, kind: 'proposal.created', groupId: g.groupId, own: 'true' });
			expect(own[0].readAt).not.toBeNull();
			// Only the earlier proposal (from the member) is unread.
			expect((await listNotifications(g.admin)).unread).toBe(1);

			const pushedTo = push.mock.calls.flatMap(([userIds]) => userIds);
			expect(pushedTo.sort()).toEqual([g.owner, g.member].sort());
		} finally {
			push.mockRestore();
		}
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

		// Confirmed proposals take no more admin overrides (replies can still change: see below).
		expect((await apiError(override(p.id, g.admin, g.member, 'declined'))).code).toBe('conflict');
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

	test('a maybe keeps it open; the proposer can still confirm anyway, and the message counts the maybes', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(12));
		await respond(p.id, g.admin, 'accepted');
		const open = await respond(p.id, g.member, 'maybe');
		expect(open.status).toBe('open');
		expect(open.responses.find((r) => r.userId === g.member)?.response).toBe('maybe');
		const confirmed = await confirmAnyway(p.id, g.owner);
		expect(confirmed).toMatchObject({ status: 'confirmed', confirmedBy: g.owner });
		const [note] = await db.select().from(messages).where(and(eq(messages.groupId, g.groupId), eq(messages.kind, 'system')));
		expect(note.body).toContain('2 of 3 can make it, 1 maybe');
		// A maybe changing to a firm yes later just updates; to no isn't dropping out (they never said yes).
		expect((await respond(p.id, g.member, 'declined')).needsReview).toBe(false);
	});

	test('everyone saying a firm yes after a maybe confirms it', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(13));
		await respond(p.id, g.admin, 'maybe');
		await respond(p.id, g.member, 'accepted');
		expect((await respond(p.id, g.admin, 'accepted')).status).toBe('confirmed');
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

describe('confirmAnyway', () => {
	test('the proposer can confirm once someone else can make it; who is coming stays recorded', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(18));
		// Only the proposer has accepted so far.
		expect((await apiError(confirmAnyway(p.id, g.owner))).code).toBe('conflict');
		await respond(p.id, g.admin, 'accepted');
		await respond(p.id, g.member, 'declined');
		expect((await apiError(confirmAnyway(p.id, g.member))).code).toBe('forbidden');
		expect((await apiError(confirmAnyway(p.id, g.outsider))).code).toBe('not_found');

		const confirmed = await confirmAnyway(p.id, g.owner);
		expect(confirmed).toMatchObject({ status: 'confirmed', confirmedBy: g.owner });
		expect(confirmed.eventId).toBeTruthy();
		const byUser = Object.fromEntries(confirmed.responses.map((r) => [r.userId, r.response]));
		expect(byUser).toEqual({ [g.owner]: 'accepted', [g.admin]: 'accepted', [g.member]: 'declined' });
		expect(await getProposal(p.id, g.member)).toMatchObject({ status: 'confirmed', confirmedBy: g.owner });

		const made = await db.select().from(events).where(eq(events.proposalId, p.id));
		expect(made.length).toBe(1);
		const system = await db.select().from(messages).where(and(eq(messages.groupId, g.groupId), eq(messages.kind, 'system')));
		expect(system.map((m) => m.body)).toEqual([expect.stringContaining('(2 of 3 can make it)')]);
		expect(system[0].body!.startsWith("It's on for")).toBe(true);
		const pushed = await db.select().from(notifications).where(and(eq(notifications.groupId, g.groupId), eq(notifications.kind, 'proposal.confirmed')));
		expect(pushed.map((n) => n.userId).sort()).toEqual([g.owner, g.admin, g.member].sort());
		expect(pushed[0].title).toBe("It's on");

		// Closed now, like any confirmed proposal.
		expect((await apiError(confirmAnyway(p.id, g.owner))).code).toBe('conflict');
	});

	test('an admin can confirm someone else’s proposal with replies still missing', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.member, slot(19));
		const confirmed = await confirmAnyway(p.id, g.admin);
		expect(confirmed).toMatchObject({ status: 'confirmed', confirmedBy: g.admin });
		expect(confirmed.responses.find((r) => r.userId === g.owner)?.response).toBe('pending');
	});

	test('a proposal that confirms itself has no confirmedBy', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(20));
		await respond(p.id, g.admin, 'accepted');
		expect(await respond(p.id, g.member, 'accepted')).toMatchObject({ status: 'confirmed', confirmedBy: null });
	});

	test('a cancelled proposal cannot be confirmed', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(21));
		await respond(p.id, g.admin, 'accepted');
		await cancelProposal(p.id, g.owner);
		expect((await apiError(confirmAnyway(p.id, g.owner))).code).toBe('conflict');
	});
});

describe('changing a reply once confirmed', () => {
	async function confirmed() {
		const g = await setup();
		const p = await createProposal(g.groupId, g.member, slot(22));
		await respond(p.id, g.admin, 'accepted');
		await respond(p.id, g.owner, 'accepted');
		return { g, p: await getProposal(p.id, g.owner) };
	}

	test('someone who was coming can drop out: the plan stays confirmed and needs review', async () => {
		const { g, p } = await confirmed();
		expect(p).toMatchObject({ status: 'confirmed', needsReview: false });

		const after = await respond(p.id, g.admin, 'declined');
		expect(after).toMatchObject({ status: 'confirmed', eventId: p.eventId, needsReview: true });
		expect(after.responses.find((r) => r.userId === g.admin)).toMatchObject({ response: 'declined', droppedOut: true });
		expect(await db.query.events.findFirst({ where: eq(events.id, p.eventId!) })).toBeTruthy();

		// The proposer (and the owner, an admin) hear about it; the one who dropped out doesn't.
		const told = await db.select().from(notifications).where(and(eq(notifications.groupId, g.groupId), eq(notifications.kind, 'proposal.changed')));
		expect(told.map((n) => n.userId).sort()).toEqual([g.owner, g.member].sort());
		expect(told[0].data).toMatchObject({ proposalId: p.id });
		expect(told[0].body).toContain('any more');
		const system = await db.select().from(messages).where(and(eq(messages.groupId, g.groupId), eq(messages.kind, 'system')));
		expect(system.some((m) => m.body?.endsWith("can't make it any more"))).toBe(true);

		// Changing back just updates, and clears the review.
		const back = await respond(p.id, g.admin, 'accepted');
		expect(back).toMatchObject({ status: 'confirmed', needsReview: false });
		expect(back.responses.find((r) => r.userId === g.admin)).toMatchObject({ response: 'accepted', droppedOut: false });
	});

	test('keeping the plan clears the review; only the proposer or an admin can', async () => {
		const { g, p } = await confirmed();
		await respond(p.id, g.owner, 'declined');
		expect((await apiError(keepPlan(p.id, g.outsider))).code).toBe('not_found');
		const g2member = await fx.user();
		await fx.join(g.groupId, g2member);
		expect((await apiError(keepPlan(p.id, g2member))).code).toBe('forbidden');

		const kept = await keepPlan(p.id, g.member);
		expect(kept).toMatchObject({ status: 'confirmed', needsReview: false });
		expect(kept.responses.find((r) => r.userId === g.owner)).toMatchObject({ response: 'declined', droppedOut: false });
		// Someone who joined after it was confirmed wasn't asked, so can't reply.
		expect((await apiError(respond(p.id, g2member, 'accepted'))).code).toBe('conflict');
	});

	test('cancelling the plan deletes its event and tells everyone', async () => {
		const { g, p } = await confirmed();
		await respond(p.id, g.admin, 'declined');
		const plain = await fx.user();
		await fx.join(g.groupId, plain);
		expect((await apiError(cancelProposal(p.id, plain))).code).toBe('forbidden');

		await cancelProposal(p.id, g.owner);
		const after = await getProposal(p.id, g.member);
		expect(after).toMatchObject({ status: 'cancelled', needsReview: false });
		expect(await db.query.events.findFirst({ where: eq(events.id, p.eventId!) })).toBeUndefined();
		const system = await db.select().from(messages).where(and(eq(messages.groupId, g.groupId), eq(messages.kind, 'system')));
		expect(system.some((m) => m.body?.includes('cancelled the plan for'))).toBe(true);
		const told = await db.select().from(notifications).where(and(eq(notifications.groupId, g.groupId), eq(notifications.kind, 'proposal.changed')));
		expect(told.filter((n) => n.body.includes('cancelled')).map((n) => n.userId).sort()).toEqual([g.admin, g.member, plain].sort());

		expect((await apiError(respond(p.id, g.member, 'declined'))).code).toBe('conflict');
		expect((await apiError(keepPlan(p.id, g.owner))).code).toBe('conflict');
		expect((await apiError(cancelProposal(p.id, g.owner))).code).toBe('conflict');
	});

	test('someone who never replied can still say either way, without a review', async () => {
		const g = await setup();
		const p = await createProposal(g.groupId, g.owner, slot(23));
		await respond(p.id, g.admin, 'accepted');
		await confirmAnyway(p.id, g.owner);
		const declined = await respond(p.id, g.member, 'declined');
		expect(declined).toMatchObject({ status: 'confirmed', needsReview: false });
		expect((await respond(p.id, g.member, 'accepted')).responses.find((r) => r.userId === g.member)?.response).toBe('accepted');
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
