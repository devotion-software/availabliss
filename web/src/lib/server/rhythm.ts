/**
 * Groups that meet regularly ("roughly monthly"): after each plan, the next free time near that
 * rhythm is suggested on request, and (if the group wants) posted in the chat as "Same again?".
 */
import { and, desc, eq, gt, lte } from 'drizzle-orm';
import type { Rhythm, Slot } from '#lib/types';
import { db, isDuplicateKey } from './db';
import { events, groups, proposals, sentReminders } from './db/schema';
import { getGroupRow, memberIds, requireMember, type GroupRow } from './groups';
import { addDays, dateOf } from './availability/time';
import { suggestionAvailability } from './availability/service';
import { postMessage } from './chat';
import { notify } from './notifications';

/** The date a rhythm points at after `last` (YYYY-MM-DD in the group's zone). */
export function nextTarget(last: string, every: Rhythm['every']): string {
	const weeks = { week: 1, 'two-weeks': 2, 'three-weeks': 3 } as const;
	if (every in weeks) return addDays(last, 7 * weeks[every as keyof typeof weeks]);
	const months = { month: 1, 'two-months': 2, 'three-months': 3, 'six-months': 6, year: 12 } as const;
	const step = months[every as keyof typeof months];
	// Monthly and longer: the same weekday in the same week of the month ("the first Friday").
	const [y, m, d] = last.split('-').map(Number);
	const weekday = new Date(Date.UTC(y, m - 1, d)).getUTCDay();
	const nth = Math.ceil(d / 7);
	const ny = y + Math.floor((m - 1 + step) / 12),
		nm = ((m - 1 + step) % 12) + 1;
	const firstWeekday = new Date(Date.UTC(ny, nm - 1, 1)).getUTCDay();
	let day = 1 + ((weekday - firstWeekday + 7) % 7) + (nth - 1) * 7;
	const daysInMonth = new Date(Date.UTC(ny, nm, 0)).getUTCDate();
	while (day > daysInMonth) day -= 7;
	return `${ny}-${String(nm).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

/** Of `slots`, the one nearest `target` (everyone free beats someone missing). */
export function nearest(slots: readonly Slot[], target: string, tz: string): Slot | null {
	const score = (s: Slot) => {
		const days = Math.abs(Date.parse(`${dateOf(Date.parse(s.start), tz)}T00:00:00Z`) - Date.parse(`${target}T00:00:00Z`)) / 86_400_000;
		return days + (s.missingMemberIds?.length ? 10 : 0);
	};
	return [...slots].sort((a, b) => score(a) - score(b) || a.start.localeCompare(b.start))[0] ?? null;
}

export interface NextSuggestion {
	/** The date the rhythm points at. */
	target: string;
	/** The free time nearest it (within a week or so either side), or null if none. */
	slot: Slot | null;
}

/** How far either side of the target to look. */
const BEFORE_DAYS = 6,
	AFTER_DAYS = 10;

async function suggestFor(group: GroupRow, now: number): Promise<NextSuggestion | null> {
	if (!group.rhythm) return null;
	const tz = group.requirement.timezone;
	const [last] = await db.select({ start: events.start }).from(events).where(eq(events.groupId, group.id)).orderBy(desc(events.start)).limit(1);
	const base = last ? dateOf(last.start.getTime(), tz) : dateOf(now, tz);
	let target = nextTarget(base, group.rhythm.every);
	const today = dateOf(now, tz);
	while (target < today) target = nextTarget(target, group.rhythm.every);
	const from = [addDays(target, -BEFORE_DAYS), today].sort()[1];
	const slots = (await suggestionAvailability(group, from, addDays(target, AFTER_DAYS))).filter((s) => Date.parse(s.start) > now);
	return { target, slot: nearest(slots, target, tz) };
}

/** GET /groups/:id/next: the next free time near the group's rhythm (null without a rhythm). */
export async function nextSuggestion(groupId: string, userId: string, now = Date.now()): Promise<NextSuggestion | null> {
	await requireMember(groupId, userId);
	return suggestFor(await getGroupRow(groupId), now);
}

/**
 * For groups that asked for it: once a plan has ended and nothing newer is planned, post
 * "Same again?" with the suggested time (once per plan). Run from the reminders job.
 */
export async function promptNextPlans(now = Date.now()): Promise<number> {
	const since = new Date(now - 2 * 86_400_000);
	const ended = await db
		.select({ event: events, group: groups })
		.from(events)
		.innerJoin(groups, eq(groups.id, events.groupId))
		.where(and(gt(events.end, since), lte(events.end, new Date(now))));
	let posted = 0;
	for (const { event, group } of ended) {
		if (!group.rhythm?.prompt || group.archivedAt) continue;
		// Something newer already planned or proposed: nothing to prompt.
		const [later] = await db
			.select({ id: events.id })
			.from(events)
			.where(and(eq(events.groupId, group.id), gt(events.start, event.start)))
			.limit(1);
		const [open] = await db
			.select({ id: proposals.id })
			.from(proposals)
			.where(and(eq(proposals.groupId, group.id), eq(proposals.status, 'open'), gt(proposals.start, new Date(now))))
			.limit(1);
		if (later || open) continue;
		try {
			await db.insert(sentReminders).values({ kind: 'rhythm', targetId: event.id, startsAt: event.start });
		} catch (err) {
			if (isDuplicateKey(err)) continue;
			throw err;
		}
		const suggestion = await suggestFor(group, now);
		const body = suggestion?.slot
			? `Same again? ${suggestion.slot.label} works${suggestion.slot.missingMemberIds?.length ? ' for most of you' : ' for everyone'}. Propose it from the calendar.`
			: 'Same again? Nothing near the usual time works for everyone yet. Have a look at the calendar.';
		await postMessage(group.id, null, { kind: 'system', body });
		await notify(await memberIds(group.id), {
			kind: 'reminder',
			groupId: group.id,
			title: group.name,
			body,
			data: { reminder: 'next', ...(suggestion?.slot && { start: suggestion.slot.start, end: suggestion.slot.end }) }
		});
		posted++;
	}
	return posted;
}
