/** Loads a group's members, busy time and holidays, then runs the engine. */
import { and, eq } from 'drizzle-orm';
import type { Availability, DayAvailability, Requirement } from '#lib/types';
import { db } from '../db';
import { groupMembers, user } from '../db/schema';
import { badRequest } from '../http';
import { getGroupRow, memberIds, requireMember } from '../groups';
import { loadBusy, syncedUserIds } from '../busy';
import { bankHolidays, schoolHolidayRanges } from '../holidays';
import { today } from '../requirements';
import { computeAvailability, computeDay, type EngineInput } from './engine';
import { addDays, daysBetween, startOfDate } from './time';

/** Longest range one request may cover. */
const MAX_RANGE_DAYS = 120;
/** Slots can begin this many days before the requested range (multi-day spans). */
const LOOKBACK_DAYS = 14;

type GroupRow = Awaited<ReturnType<typeof getGroupRow>>;

/** Bank holidays follow the group owner's region. */
async function ownerRegion(groupId: string) {
	const [row] = await db
		.select({ country: user.country, subdivision: user.subdivision })
		.from(groupMembers)
		.innerJoin(user, eq(user.id, groupMembers.userId))
		.where(and(eq(groupMembers.groupId, groupId), eq(groupMembers.role, 'owner')))
		.limit(1);
	return row ?? { country: null, subdivision: null };
}

async function engineInput(group: GroupRow, from: string, to: string): Promise<EngineInput> {
	const req = group.requirement;
	const tz = req.timezone;
	const ids = await memberIds(group.id);
	const loadFrom = addDays(from, -LOOKBACK_DAYS);
	const loadTo = addDays(to, LOOKBACK_DAYS);
	const holidayRange = { from: loadFrom, to: loadTo };

	const [busy, synced, region, school] = await Promise.all([
		loadBusy(ids, startOfDate(loadFrom, tz), startOfDate(loadTo, tz)),
		syncedUserIds(ids),
		ownerRegion(group.id),
		schoolHolidayRanges(req, holidayRange)
	]);

	return {
		requirement: req,
		from,
		to,
		members: ids.map((userId) => ({ userId, synced: synced.has(userId), busy: busy.get(userId) ?? [] })),
		bankHolidays: await bankHolidays(region.country, region.subdivision, holidayRange),
		schoolHolidays: school,
		now: Date.now()
	};
}

export async function groupAvailability(groupId: string, userId: string, from: string, to: string): Promise<Availability> {
	if (to < from) throw badRequest('`to` is before `from`');
	if (daysBetween(from, to) > MAX_RANGE_DAYS) throw badRequest(`Ask for at most ${MAX_RANGE_DAYS} days at a time`);
	await requireMember(groupId, userId);
	const group = await getGroupRow(groupId);
	return computeAvailability(await engineInput(group, from, to));
}

export async function groupDay(groupId: string, userId: string, date: string): Promise<DayAvailability> {
	await requireMember(groupId, userId);
	const group = await getGroupRow(groupId);
	return computeDay(await engineInput(group, date, date), date);
}

/** How many slots work from today to the end of the group's search range (capped). */
export async function upcomingSlotCount(group: GroupRow): Promise<number> {
	const from = today(group.requirement.timezone);
	if (group.requirement.range.to < from) return 0;
	const to = [group.requirement.range.to, addDays(from, MAX_RANGE_DAYS)].sort()[0];
	return computeAvailability(await engineInput(group, from, to)).slots.length;
}

/**
 * How many slots a requirement produces before anyone's busy time is considered
 * ("13 weekends fit this timing"), so the timing editor can preview before saving.
 * Bank holidays use the editing user's region.
 */
export async function previewRequirement(userId: string, requirement: Requirement) {
	const from = [today(requirement.timezone), requirement.range.from].sort()[1];
	const to = [requirement.range.to, addDays(from, MAX_RANGE_DAYS)].sort()[0];
	if (to < from) return { count: 0, from, to };
	const region = (await db.query.user.findFirst({ where: eq(user.id, userId), columns: { country: true, subdivision: true } })) ?? {
		country: null,
		subdivision: null
	};
	const holidayRange = { from: addDays(from, -LOOKBACK_DAYS), to };
	const result = computeAvailability({
		requirement,
		from,
		to,
		members: [],
		bankHolidays: await bankHolidays(region.country, region.subdivision, holidayRange),
		schoolHolidays: await schoolHolidayRanges(requirement, holidayRange),
		now: Date.now()
	});
	return { count: result.slots.length, from, to };
}

/**
 * What the AI planner may see about a group's coming weeks: the slots that work
 * for everyone and named holidays. No member names or busy time.
 */
export async function planningContext(group: GroupRow, maxSlots = 25) {
	const from = [today(group.requirement.timezone), group.requirement.range.from].sort()[1];
	const to = [group.requirement.range.to, addDays(from, MAX_RANGE_DAYS)].sort()[0];
	if (to < from) return { from, to, slots: [], holidays: [], members: 0, synced: 0 };
	const input = await engineInput(group, from, to);
	const result = computeAvailability(input);
	return {
		from,
		to,
		slots: result.slots.slice(0, maxSlots),
		holidays: result.days.filter((d) => d.holiday).map((d) => ({ date: d.date, name: d.holiday! })),
		members: input.members.length,
		synced: input.members.filter((m) => m.synced).length
	};
}
