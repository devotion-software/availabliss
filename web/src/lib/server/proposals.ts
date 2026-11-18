/**
 * Proposals: someone suggests a time, everyone accepts or declines, admins can
 * override a response. When every member has accepted, the proposal is
 * confirmed, an event is created and everyone is notified. The proposer or an
 * admin can also confirm it before then ("Confirm anyway"), once someone else
 * can make it.
 *
 * Once confirmed, members can still change their reply. Someone who was coming
 * saying they can't any more marks the plan for review: the proposer and admins
 * are told, and one of them keeps the plan or cancels it (deleting its event).
 */
import { and, desc, eq, inArray } from 'drizzle-orm';
import type { Proposal, ResponseKind } from '#lib/types';
import { db } from './db';
import { events, groupMembers, proposalResponses, proposals, user } from './db/schema';
import { conflict, forbidden, notFound } from './http';
import { newId } from './ids';
import { getGroupRow, memberIds, requireMember } from './groups';
import { publishToGroup } from './realtime';
import { unarchiveOnActivity } from './auto-archive';
import { notify } from './notifications';
import { postMessage } from './chat';
import { insertEvent, removeEvent, toGroupEvent } from './events';
import { slotLabel } from './availability/labels';
import { sendConfirmedEmail } from './email';
import { env } from './env';
import { loadBusy, type LabelledBusy } from './busy';
import { displayName } from './names';

type ProposalRow = typeof proposals.$inferSelect;
type ResponseRow = typeof proposalResponses.$inferSelect;

const toProposal = (row: ProposalRow, responses: ResponseRow[], busyMemberIds: string[]): Proposal => ({
	id: row.id,
	groupId: row.groupId,
	title: row.title,
	location: row.location,
	note: row.note,
	start: row.start.toISOString(),
	end: row.end.toISOString(),
	allDay: row.allDay,
	status: row.status,
	createdBy: row.createdBy,
	createdAt: row.createdAt.toISOString(),
	eventId: row.eventId,
	confirmedBy: row.confirmedBy ?? null,
	responses: responses.map((r) => ({
		userId: r.userId,
		response: r.response,
		setBy: r.setBy,
		updatedAt: r.updatedAt.toISOString(),
		droppedOut: row.status === 'confirmed' && r.droppedOut
	})),
	needsReview: row.status === 'confirmed' && responses.some((r) => r.droppedOut),
	busyMemberIds
});

async function load(rows: ProposalRow[]): Promise<Proposal[]> {
	if (!rows.length) return [];
	const responses = await db
		.select()
		.from(proposalResponses)
		.where(inArray(proposalResponses.proposalId, rows.map((r) => r.id)));
	const byProposal = new Map<string, ResponseRow[]>(rows.map((r) => [r.id, []]));
	for (const r of responses) byProposal.get(r.proposalId)?.push(r);

	// Who can't make each open proposal, so people see the clash before they accept
	// or decline. One busy load for everyone over the span of all open proposals.
	const open = rows.filter((r) => r.status === 'open');
	const busy = open.length
		? await loadBusy(
				[...new Set(open.flatMap((r) => byProposal.get(r.id)!.map((x) => x.userId)))],
				Math.min(...open.map((r) => r.start.getTime())),
				Math.max(...open.map((r) => r.end.getTime()))
			)
		: new Map<string, LabelledBusy[]>();

	return rows.map((row) => {
		const own = byProposal.get(row.id)!;
		const start = row.start.getTime();
		const end = row.end.getTime();
		const busyIds =
			row.status === 'open'
				? own.map((r) => r.userId).filter((id) => busy.get(id)?.some((b) => b.start < end && b.end > start))
				: [];
		return toProposal(row, own, busyIds);
	});
}

async function getRow(proposalId: string): Promise<ProposalRow> {
	const row = await db.query.proposals.findFirst({ where: eq(proposals.id, proposalId) });
	if (!row) throw notFound('Proposal not found');
	return row;
}

async function fetchProposal(proposalId: string): Promise<Proposal> {
	return (await load([await getRow(proposalId)]))[0];
}

export async function getProposal(proposalId: string, userId: string): Promise<Proposal> {
	const row = await getRow(proposalId);
	await requireMember(row.groupId, userId);
	return (await load([row]))[0];
}

export async function listProposals(groupId: string, userId: string, status?: Proposal['status']): Promise<Proposal[]> {
	await requireMember(groupId, userId);
	const rows = await db
		.select()
		.from(proposals)
		.where(and(eq(proposals.groupId, groupId), status ? eq(proposals.status, status) : undefined))
		.orderBy(desc(proposals.createdAt))
		.limit(100);
	return load(rows);
}

/** Open proposals in a group still waiting on this user. */
export async function awaitingReply(groupId: string, userId: string): Promise<string | null> {
	const [row] = await db
		.select({ id: proposals.id })
		.from(proposals)
		.innerJoin(proposalResponses, eq(proposalResponses.proposalId, proposals.id))
		.where(
			and(
				eq(proposals.groupId, groupId),
				eq(proposals.status, 'open'),
				eq(proposalResponses.userId, userId),
				eq(proposalResponses.response, 'pending')
			)
		)
		.limit(1);
	return row?.id ?? null;
}

export interface ProposalInput {
	start: string;
	end: string;
	allDay: boolean;
	title: string | null;
	location: string | null;
	note: string | null;
}

export async function createProposal(groupId: string, userId: string, input: ProposalInput): Promise<Proposal> {
	await requireMember(groupId, userId);
	const id = newId();
	const members = await memberIds(groupId);
	await db.transaction(async (tx) => {
		await tx.insert(proposals).values({
			id,
			groupId,
			createdBy: userId,
			...input,
			start: new Date(input.start),
			end: new Date(input.end)
		});
		await tx.insert(proposalResponses).values(
			members.map((memberId) => ({
				proposalId: id,
				userId: memberId,
				response: (memberId === userId ? 'accepted' : 'pending') as ResponseKind
			}))
		);
	});

	const group = await getGroupRow(groupId);
	const proposer = await db.query.user.findFirst({ where: eq(user.id, userId), columns: { name: true, email: true } });
	const when = slotLabel({ start: Date.parse(input.start), end: Date.parse(input.end) }, group.requirement);

	await postMessage(groupId, userId, { kind: 'proposal', proposalId: id });
	await unarchiveOnActivity(groupId);
	const proposal = await fetchProposal(id);
	await publishToGroup(groupId, 'proposal.created', proposal);
	await notify(
		members.filter((m) => m !== userId),
		{
			kind: 'proposal.created',
			groupId,
			title: group.name,
			body: `${displayName(proposer)} proposed ${when}. Can you make it?`,
			data: { proposalId: id }
		}
	);
	return (await confirmIfEveryoneAccepted(proposal)) ?? proposal;
}

async function setResponse(proposalId: string, targetId: string, response: ResponseKind, setBy: string | null) {
	await db
		.insert(proposalResponses)
		.values({ proposalId, userId: targetId, response, setBy })
		.onDuplicateKeyUpdate({ set: { response, setBy, updatedAt: new Date() } });
	const proposal = await fetchProposal(proposalId);
	await publishToGroup(proposal.groupId, 'proposal.updated', proposal);
	return (await confirmIfEveryoneAccepted(proposal)) ?? proposal;
}

async function requireOpen(proposalId: string): Promise<ProposalRow> {
	const row = await getRow(proposalId);
	if (row.status !== 'open') throw conflict(`This proposal is already ${row.status}`);
	return row;
}

export async function respond(proposalId: string, userId: string, response: 'accepted' | 'declined'): Promise<Proposal> {
	const row = await getRow(proposalId);
	await requireMember(row.groupId, userId);
	if (row.status === 'confirmed') return changeConfirmedResponse(row, userId, response);
	if (row.status !== 'open') throw conflict(`This proposal is already ${row.status}`);
	return setResponse(proposalId, userId, response, null);
}

/**
 * A reply changed after the plan was confirmed. It stays confirmed. Someone who was coming
 * saying they can't any more marks it for review (`droppedOut`) and tells the proposer and
 * admins, who keep it or cancel it; anyone else's change (including coming after all) just
 * updates. Only people asked at the time can reply: members who joined since can't.
 */
async function changeConfirmedResponse(row: ProposalRow, userId: string, response: 'accepted' | 'declined'): Promise<Proposal> {
	const current = await db.query.proposalResponses.findFirst({
		where: and(eq(proposalResponses.proposalId, row.id), eq(proposalResponses.userId, userId))
	});
	if (!current) throw conflict('You joined after this was confirmed');
	if (current.response === response) return fetchProposal(row.id);

	const droppedOut = current.response === 'accepted' && response === 'declined';
	await db
		.update(proposalResponses)
		.set({ response, setBy: null, droppedOut, updatedAt: new Date() })
		.where(and(eq(proposalResponses.proposalId, row.id), eq(proposalResponses.userId, userId)));
	const proposal = await fetchProposal(row.id);
	await publishToGroup(row.groupId, 'proposal.updated', proposal);

	if (droppedOut) {
		const [group, who, reviewers] = await Promise.all([
			getGroupRow(row.groupId),
			db.query.user.findFirst({ where: eq(user.id, userId), columns: { name: true, email: true } }),
			reviewerIds(row)
		]);
		const name = displayName(who);
		const when = slotLabel({ start: row.start.getTime(), end: row.end.getTime() }, group.requirement);
		await postMessage(row.groupId, null, { kind: 'system', body: `${name} can't make it any more` });
		await notify(
			reviewers.filter((id) => id !== userId),
			{
				kind: 'proposal.changed',
				groupId: row.groupId,
				title: group.name,
				body: `${name} can't make ${when} any more. Keep the plan, or find another date?`,
				data: { proposalId: row.id }
			}
		);
	}
	return proposal;
}

/** Who can keep or cancel a confirmed plan: its proposer and the group's admins (and owner). */
async function reviewerIds(row: ProposalRow): Promise<string[]> {
	const admins = await db
		.select({ userId: groupMembers.userId })
		.from(groupMembers)
		.where(and(eq(groupMembers.groupId, row.groupId), inArray(groupMembers.role, ['owner', 'admin'])));
	const members = await memberIds(row.groupId);
	return [...new Set([row.createdBy, ...admins.map((a) => a.userId)])].filter((id) => members.includes(id));
}

async function requireReviewer(row: ProposalRow, userId: string, action: string) {
	const role = await requireMember(row.groupId, userId);
	if (row.createdBy !== userId && role === 'member') throw forbidden(`Only the proposer or an admin can ${action} this`);
}

/**
 * "Keep the plan": after someone said they can't make it any more, the proposer or an admin
 * keeps it as it is. Clears the review; those people stay as can't make it.
 */
export async function keepPlan(proposalId: string, userId: string): Promise<Proposal> {
	const row = await getRow(proposalId);
	await requireReviewer(row, userId, 'keep');
	if (row.status !== 'confirmed') throw conflict(`This proposal is ${row.status}`);
	await db.update(proposalResponses).set({ droppedOut: false }).where(eq(proposalResponses.proposalId, proposalId));
	const proposal = await fetchProposal(proposalId);
	await publishToGroup(row.groupId, 'proposal.updated', proposal);
	return proposal;
}

export async function override(proposalId: string, adminId: string, targetId: string, response: ResponseKind): Promise<Proposal> {
	const row = await requireOpen(proposalId);
	await requireMember(row.groupId, adminId, 'admin');
	await requireMember(row.groupId, targetId);
	return setResponse(proposalId, targetId, response, adminId);
}

export async function cancelProposal(proposalId: string, userId: string): Promise<void> {
	const row = await getRow(proposalId);
	await requireReviewer(row, userId, 'cancel');
	if (row.status === 'confirmed') return cancelPlan(row, userId);
	if (row.status !== 'open') throw conflict(`This proposal is already ${row.status}`);
	// Guarded so a cancel racing the last acceptance can't cancel a confirmed proposal.
	const [result] = await db
		.update(proposals)
		.set({ status: 'cancelled' })
		.where(and(eq(proposals.id, proposalId), eq(proposals.status, 'open')));
	if (!result.affectedRows) throw conflict(`This proposal is already ${(await getRow(proposalId)).status}`);
	await publishToGroup(row.groupId, 'proposal.updated', await fetchProposal(proposalId));
}

/**
 * Cancels a confirmed plan (e.g. "Cancel and find another date" after someone dropped out):
 * marks it cancelled, deletes its event, and tells the group in the chat and everyone else
 * with a notification.
 */
async function cancelPlan(row: ProposalRow, userId: string): Promise<void> {
	// Guarded so two cancels (or a cancel racing another) only cancel once.
	const [result] = await db
		.update(proposals)
		.set({ status: 'cancelled' })
		.where(and(eq(proposals.id, row.id), eq(proposals.status, 'confirmed')));
	if (!result.affectedRows) throw conflict(`This proposal is already ${(await getRow(row.id)).status}`);
	await db.update(proposalResponses).set({ droppedOut: false }).where(eq(proposalResponses.proposalId, row.id));
	const event = row.eventId ? await db.query.events.findFirst({ where: eq(events.id, row.eventId) }) : null;
	if (event) await removeEvent(event);

	const [group, who] = await Promise.all([
		getGroupRow(row.groupId),
		db.query.user.findFirst({ where: eq(user.id, userId), columns: { name: true, email: true } })
	]);
	const name = displayName(who);
	const when = slotLabel({ start: row.start.getTime(), end: row.end.getTime() }, group.requirement);
	await postMessage(row.groupId, null, { kind: 'system', body: `${name} cancelled the plan for ${when}` });
	await publishToGroup(row.groupId, 'proposal.updated', await fetchProposal(row.id));
	await notify(
		(await memberIds(row.groupId)).filter((id) => id !== userId),
		{
			kind: 'proposal.changed',
			groupId: row.groupId,
			title: group.name,
			body: `${name} cancelled the plan for ${when}.`,
			data: { proposalId: row.id }
		}
	);
}

/**
 * Confirms the proposal when every current member has accepted. Returns it if
 * confirmed. Takes the freshly loaded proposal so callers don't load it twice.
 */
async function confirmIfEveryoneAccepted(proposal: Proposal): Promise<Proposal | null> {
	if (proposal.status !== 'open') return null;
	const members = await memberIds(proposal.groupId);
	const accepted = new Set(proposal.responses.filter((r) => r.response === 'accepted').map((r) => r.userId));
	if (!members.every((m) => accepted.has(m))) return null;
	return confirm(proposal, members, null);
}

/**
 * "Confirm anyway": the proposer or an admin confirms an open proposal before everyone
 * has accepted (someone can't make it, or hasn't replied). Needs at least one acceptance
 * besides theirs, so a plan isn't confirmed that nobody else can make. Whoever hasn't
 * accepted keeps their reply (declined or pending), so the plan shows who's coming.
 */
export async function confirmAnyway(proposalId: string, userId: string): Promise<Proposal> {
	const row = await requireOpen(proposalId);
	const role = await requireMember(row.groupId, userId);
	if (row.createdBy !== userId && role === 'member') throw forbidden('Only the proposer or an admin can confirm this');
	const proposal = await fetchProposal(proposalId);
	if (!proposal.responses.some((r) => r.response === 'accepted' && r.userId !== userId)) {
		throw conflict('Nobody else can make it yet');
	}
	const members = await memberIds(proposal.groupId);
	const everyone = members.every((m) => proposal.responses.some((r) => r.userId === m && r.response === 'accepted'));
	const result = await confirm(proposal, members, everyone ? null : userId);
	// Cancelled meanwhile.
	if (result.status !== 'confirmed') throw conflict(`This proposal is already ${result.status}`);
	return result;
}

/**
 * Flips the proposal to confirmed, creates its event and tells everyone. `confirmedBy` is
 * who confirmed it anyway, or null when everyone accepted.
 */
async function confirm(proposal: Proposal, members: string[], confirmedBy: string | null): Promise<Proposal> {
	const proposalId = proposal.id;
	const group = await getGroupRow(proposal.groupId);
	const eventId = await db.transaction(async (tx) => {
		// Claim the confirmation first. When the last members accept at the same
		// moment both requests get here; only the one that flips open → confirmed
		// creates the event and sends the messages, pushes and emails.
		const [claimed] = await tx
			.update(proposals)
			.set({ status: 'confirmed', confirmedBy })
			.where(and(eq(proposals.id, proposalId), eq(proposals.status, 'open')));
		if (!claimed.affectedRows) return null;
		const id = await insertEvent(tx, proposal.groupId, proposal.createdBy, {
			title: proposal.title ?? group.name,
			start: proposal.start,
			end: proposal.end,
			allDay: proposal.allDay,
			location: proposal.location,
			note: proposal.note,
			proposalId
		});
		await tx.update(proposals).set({ eventId: id }).where(eq(proposals.id, proposalId));
		return id;
	});
	// Lost the race: another request confirmed it (and sent everything) first.
	if (!eventId) return fetchProposal(proposalId);

	// Only the status and event changed, and confirmed proposals list nobody as busy.
	const confirmed: Proposal = { ...proposal, status: 'confirmed', eventId, confirmedBy, busyMemberIds: [] };
	const when = slotLabel({ start: Date.parse(proposal.start), end: Date.parse(proposal.end) }, group.requirement);
	const event = toGroupEvent((await db.query.events.findFirst({ where: eq(events.id, eventId) }))!);
	const coming = proposal.responses.filter((r) => r.response === 'accepted').length;
	const total = proposal.responses.length;
	const count = `${coming} of ${total} can make it`;

	await postMessage(proposal.groupId, null, {
		kind: 'system',
		body: confirmedBy ? `It's on for ${when} (${count})` : `Everyone's in for ${when}`,
		eventId
	});
	await publishToGroup(proposal.groupId, 'proposal.confirmed', { proposal: confirmed, event });
	await notify(members, {
		kind: 'proposal.confirmed',
		groupId: proposal.groupId,
		title: confirmedBy ? "It's on" : "Everyone's in",
		body: confirmedBy
			? `${group.name}: ${when}. ${count}. Tap to add it to your calendar.`
			: `${group.name}: ${when}. Tap to add it to your calendar.`,
		data: { proposalId, eventId }
	});
	await emailConfirmation(members, group.name, when, proposalId, confirmedBy ? count : null);
	return confirmed;
}

async function emailConfirmation(userIds: string[], groupName: string, when: string, proposalId: string, count: string | null) {
	const people = await db.select({ email: user.email }).from(user).where(inArray(user.id, userIds));
	const url = `${env.baseUrl}/app/proposals/${proposalId}`;
	await Promise.allSettled(people.map((p) => sendConfirmedEmail({ email: p.email, groupName, when, url, count })));
}

// ─── Membership changes ───────────────────────────────────────────────────

/** New members start as pending on every open proposal. */
export async function onMemberJoined(groupId: string, userId: string): Promise<void> {
	const open = await db
		.select({ id: proposals.id })
		.from(proposals)
		.where(and(eq(proposals.groupId, groupId), eq(proposals.status, 'open')));
	if (!open.length) return;
	await db
		.insert(proposalResponses)
		.values(open.map((p) => ({ proposalId: p.id, userId, response: 'pending' as const })))
		.onDuplicateKeyUpdate({ set: { userId } });
}

/** A member leaving may complete an open proposal. */
export async function onMemberLeft(groupId: string, userId: string): Promise<void> {
	const open = await db
		.select({ id: proposals.id })
		.from(proposals)
		.where(and(eq(proposals.groupId, groupId), eq(proposals.status, 'open')));
	for (const p of open) {
		await db.delete(proposalResponses).where(and(eq(proposalResponses.proposalId, p.id), eq(proposalResponses.userId, userId)));
		await confirmIfEveryoneAccepted(await fetchProposal(p.id));
	}
}
