/**
 * Reminders, sent by a job every few minutes (one instance at a time, see hooks.server.ts):
 * - before a confirmed plan, to the people going: the day before ("Curry night is tomorrow at
 *   19:00") and an hour before ("… starts at 19:00"), as each person chose (`eventReminders`);
 * - a day before an open proposal, to members who haven't replied yet.
 * Each goes once (sent_reminders), keyed by the plan's start so a plan that moves is reminded again.
 */
import { and, eq, gt, inArray, lte } from 'drizzle-orm';
import type { EventReminders } from '#lib/types';
import { db, isDuplicateKey } from './db';
import { events, groupMembers, groups, proposalResponses, proposals, sentReminders, user } from './db/schema';
import { dayLabel, slotLabel, timeLabel } from './availability/labels';
import { addDays, dateOf } from './availability/time';
import { notify } from './notifications';

export const REMINDER_CHECK_INTERVAL_MS = 5 * 60 * 1000;

const HOUR = 60 * 60 * 1000;
/** Day-before reminders for timed plans go 24 hours ahead; for all-day plans at 09:00 the day before. */
const DAY_BEFORE = 24 * HOUR;
const ALL_DAY_BEFORE = 15 * HOUR;
/** A timed plan made at short notice skips "tomorrow" when it's this close (the hour-before one is enough). */
const TOO_CLOSE_FOR_DAY = 3 * HOUR;

type Kind = 'day' | 'hour';

/** Which reminders are due for a plan right now. */
export function dueReminders(start: number, allDay: boolean, now: number): Kind[] {
	if (start <= now) return [];
	const until = start - now;
	if (allDay) return until <= ALL_DAY_BEFORE ? ['day'] : [];
	if (until <= HOUR) return ['hour'];
	if (until <= DAY_BEFORE && until > TOO_CLOSE_FOR_DAY) return ['day'];
	return [];
}

/** "today", "tomorrow" or "on Fri 13 Nov", in the group's time zone. */
function whenWord(start: number, now: number, tz: string): string {
	const day = dateOf(start, tz);
	if (day === dateOf(now, tz)) return 'today';
	if (day === addDays(dateOf(now, tz), 1)) return 'tomorrow';
	return `on ${dayLabel(start, tz)}`;
}

/** The body of an event reminder. */
export function reminderText(kind: Kind, title: string, start: number, allDay: boolean, now: number, tz: string): string {
	if (kind === 'hour') return `${title} starts at ${timeLabel(start, tz)}`;
	const when = whenWord(start, now, tz);
	return allDay ? `${title} is ${when}` : `${title} is ${when} at ${timeLabel(start, tz)}`;
}

/** Claims a reminder; false if it was sent already (by this run or another instance). */
async function claim(kind: string, targetId: string, startsAt: Date): Promise<boolean> {
	try {
		await db.insert(sentReminders).values({ kind, targetId, startsAt });
		return true;
	} catch (err) {
		if (isDuplicateKey(err)) return false;
		throw err;
	}
}

const wants = (setting: string, kind: Kind) => setting === 'both' || setting === (kind satisfies EventReminders);

export async function sendDueReminders(now = Date.now()): Promise<number> {
	let sent = 0;
	const horizon = new Date(now + DAY_BEFORE);

	// ─── Confirmed plans ───
	const upcoming = await db
		.select({ event: events, groupName: groups.name, requirement: groups.requirement })
		.from(events)
		.innerJoin(groups, eq(groups.id, events.groupId))
		.where(and(gt(events.start, new Date(now)), lte(events.start, horizon)));
	for (const { event, groupName, requirement } of upcoming) {
		const start = event.start.getTime();
		for (const kind of dueReminders(start, event.allDay, now)) {
			if (!(await claim(`event.${kind}`, event.id, event.start))) continue;
			const going = await goingTo(event.groupId, event.proposalId);
			if (!going.length) continue;
			const prefs = await db.select({ id: user.id, reminders: user.eventReminders }).from(user).where(inArray(user.id, going));
			const to = prefs.filter((p) => wants(p.reminders, kind)).map((p) => p.id);
			if (!to.length) continue;
			await notify(to, {
				kind: 'reminder',
				groupId: event.groupId,
				title: groupName,
				body: reminderText(kind, event.title, start, event.allDay, now, requirement.timezone),
				data: { eventId: event.id, reminder: kind, ...(event.proposalId && { proposalId: event.proposalId }) }
			});
			sent += to.length;
		}
	}

	// ─── Proposals still waiting on someone ───
	const open = await db
		.select({ proposal: proposals, groupName: groups.name, requirement: groups.requirement })
		.from(proposals)
		.innerJoin(groups, eq(groups.id, proposals.groupId))
		.where(and(eq(proposals.status, 'open'), gt(proposals.start, new Date(now)), lte(proposals.start, horizon)));
	for (const { proposal, groupName, requirement } of open) {
		if (!(await claim('proposal.reply', proposal.id, proposal.start))) continue;
		const members = new Set(
			(await db.select({ userId: groupMembers.userId }).from(groupMembers).where(eq(groupMembers.groupId, proposal.groupId))).map((m) => m.userId)
		);
		const waiting = (
			await db
				.select({ userId: proposalResponses.userId })
				.from(proposalResponses)
				.where(and(eq(proposalResponses.proposalId, proposal.id), eq(proposalResponses.response, 'pending')))
		)
			.map((r) => r.userId)
			.filter((id) => members.has(id));
		if (!waiting.length) continue;
		const span = { start: proposal.start.getTime(), end: proposal.end.getTime() };
		const what = proposal.title ? `${proposal.title} (${slotLabel(span, requirement)})` : slotLabel(span, requirement);
		await notify(waiting, {
			kind: 'reminder',
			groupId: proposal.groupId,
			title: groupName,
			body: `${what} is ${whenWord(span.start, now, requirement.timezone)}. Can you make it? Everyone's waiting on your reply.`,
			data: { proposalId: proposal.id, reminder: 'reply' }
		});
		sent += waiting.length;
	}
	return sent;
}

/**
 * Who's going to a plan: for one confirmed from a proposal, current members who said yes or maybe;
 * for an event added straight to the calendar, every member.
 */
async function goingTo(groupId: string, proposalId: string | null): Promise<string[]> {
	const members = (await db.select({ userId: groupMembers.userId }).from(groupMembers).where(eq(groupMembers.groupId, groupId))).map((m) => m.userId);
	if (!proposalId) return members;
	const yes = await db
		.select({ userId: proposalResponses.userId })
		.from(proposalResponses)
		.where(and(eq(proposalResponses.proposalId, proposalId), inArray(proposalResponses.response, ['accepted', 'maybe'])));
	const going = new Set(yes.map((r) => r.userId));
	return members.filter((id) => going.has(id));
}
