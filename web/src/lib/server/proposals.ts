/**
 * Proposals: someone suggests a time, everyone accepts or declines, admins can
 * override a response. When every member has accepted, the proposal is
 * confirmed, an event is created and everyone is notified.
 */
import { and, desc, eq, inArray } from 'drizzle-orm';
import type { Proposal, ResponseKind } from '#lib/types';
import { db } from './db';
import { events, proposalResponses, proposals, user } from './db/schema';
import { forbidden, notFound } from './http';
import { newId } from './ids';
import { getGroupRow, memberIds, requireMember } from './groups';
import { publishToGroup } from './realtime';
import { notify } from './notifications';
import { postMessage } from './chat';
import { insertEvent, toGroupEvent } from './events';
import { slotLabel } from './availability/labels';
import { sendConfirmedEmail } from './email';
import { env } from './env';
import { loadBusy, type LabelledBusy } from './busy';

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
	responses: responses.map((r) => ({
		userId: r.userId,
		response: r.response,
		setBy: r.setBy,
		updatedAt: r.updatedAt.toISOString()
	})),
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
	const proposer = await db.query.user.findFirst({ where: eq(user.id, userId), columns: { name: true } });
	const when = slotLabel({ start: Date.parse(input.start), end: Date.parse(input.end) }, group.requirement);

	await postMessage(groupId, userId, { kind: 'proposal', proposalId: id });
	const proposal = await fetchProposal(id);
	await publishToGroup(groupId, 'proposal.created', proposal);
	await notify(
		members.filter((m) => m !== userId),
		{
			kind: 'proposal.created',
			groupId,
			title: group.name,
			body: `${proposer?.name ?? 'Someone'} proposed ${when}. Can you make it?`,
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
	if (row.status !== 'open') throw forbidden(`This proposal is already ${row.status}`);
	return row;
}

export async function respond(proposalId: string, userId: string, response: 'accepted' | 'declined'): Promise<Proposal> {
	const row = await requireOpen(proposalId);
	await requireMember(row.groupId, userId);
	return setResponse(proposalId, userId, response, null);
}

export async function override(proposalId: string, adminId: string, targetId: string, response: ResponseKind): Promise<Proposal> {
	const row = await requireOpen(proposalId);
	await requireMember(row.groupId, adminId, 'admin');
	await requireMember(row.groupId, targetId);
	return setResponse(proposalId, targetId, response, adminId);
}

export async function cancelProposal(proposalId: string, userId: string): Promise<void> {
	const row = await requireOpen(proposalId);
	const role = await requireMember(row.groupId, userId);
	if (row.createdBy !== userId && role === 'member') throw forbidden('Only the proposer or an admin can cancel this');
	// Guarded so a cancel racing the last acceptance can't cancel a confirmed proposal.
	const [result] = await db
		.update(proposals)
		.set({ status: 'cancelled' })
		.where(and(eq(proposals.id, proposalId), eq(proposals.status, 'open')));
	if (!result.affectedRows) throw forbidden(`This proposal is already ${(await getRow(proposalId)).status}`);
	await publishToGroup(row.groupId, 'proposal.updated', await fetchProposal(proposalId));
}

/**
 * Confirms the proposal when every current member has accepted. Returns it if
 * confirmed. Takes the freshly loaded proposal so callers don't load it twice.
 */
async function confirmIfEveryoneAccepted(proposal: Proposal): Promise<Proposal | null> {
	const proposalId = proposal.id;
	if (proposal.status !== 'open') return null;
	const members = await memberIds(proposal.groupId);
	const accepted = new Set(proposal.responses.filter((r) => r.response === 'accepted').map((r) => r.userId));
	if (!members.every((m) => accepted.has(m))) return null;

	const group = await getGroupRow(proposal.groupId);
	const eventId = await db.transaction(async (tx) => {
		// Claim the confirmation first. When the last members accept at the same
		// moment both requests get here; only the one that flips open → confirmed
		// creates the event and sends the messages, pushes and emails.
		const [claimed] = await tx
			.update(proposals)
			.set({ status: 'confirmed' })
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
	const confirmed: Proposal = { ...proposal, status: 'confirmed', eventId, busyMemberIds: [] };
	const when = slotLabel({ start: Date.parse(proposal.start), end: Date.parse(proposal.end) }, group.requirement);
	const event = toGroupEvent((await db.query.events.findFirst({ where: eq(events.id, eventId) }))!);

	await postMessage(proposal.groupId, null, { kind: 'system', body: `Everyone's in for ${when}`, eventId });
	await publishToGroup(proposal.groupId, 'proposal.confirmed', { proposal: confirmed, event });
	await notify(members, {
		kind: 'proposal.confirmed',
		groupId: proposal.groupId,
		title: "Everyone's in",
		body: `${group.name}: ${when}. Tap to add it to your calendar.`,
		data: { proposalId, eventId }
	});
	await emailConfirmation(members, group.name, when, proposalId);
	return confirmed;
}

async function emailConfirmation(userIds: string[], groupName: string, when: string, proposalId: string) {
	const people = await db.select({ email: user.email }).from(user).where(inArray(user.id, userIds));
	const url = `${env.baseUrl}/app/proposals/${proposalId}`;
	await Promise.allSettled(people.map((p) => sendConfirmedEmail({ email: p.email, groupName, when, url })));
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
