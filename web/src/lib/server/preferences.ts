/** Members' preferred times for a group (guidelines only; see availability/preferences.ts). */
import { and, asc, eq } from 'drizzle-orm';
import type { GroupPreference, Weekday } from '#lib/types';
import { db } from './db';
import { groupPreferences } from './db/schema';
import { getGroupRow, requireMember } from './groups';
import { badRequest, notFound } from './http';
import { newId } from './ids';
import { preferenceLabel } from './availability/preferences';

const MAX_PER_MEMBER = 20;

type Row = typeof groupPreferences.$inferSelect;

function toPreference(row: Row, tz: string): GroupPreference {
	const pref = {
		id: row.id,
		userId: row.userId,
		kind: row.kind,
		from: row.fromDate,
		to: row.toDate,
		days: row.days,
		window: row.windowFrom && row.windowTo ? { from: row.windowFrom, to: row.windowTo } : null
	};
	return { ...pref, label: preferenceLabel(pref, tz) };
}

/** Every member's preferences for the group, oldest first. */
export async function groupPreferenceList(groupId: string, tz?: string): Promise<GroupPreference[]> {
	const zone = tz ?? (await getGroupRow(groupId)).requirement.timezone;
	const rows = await db.select().from(groupPreferences).where(eq(groupPreferences.groupId, groupId)).orderBy(asc(groupPreferences.createdAt));
	return rows.map((r) => toPreference(r, zone));
}

export async function listPreferences(groupId: string, userId: string): Promise<GroupPreference[]> {
	await requireMember(groupId, userId);
	return groupPreferenceList(groupId);
}

export interface PreferenceInput {
	kind: 'dates' | 'weekly';
	from?: string | null;
	to?: string | null;
	days?: Weekday[];
	window?: { from: string; to: string } | null;
}

export async function addPreference(groupId: string, userId: string, input: PreferenceInput): Promise<GroupPreference> {
	await requireMember(groupId, userId);
	if (input.kind === 'dates') {
		if (!input.from || !input.to) throw badRequest('Choose the dates you prefer');
		if (input.to < input.from) throw badRequest('The end date is before the start');
	}
	if (input.window && input.window.to <= input.window.from && input.window.to !== '00:00') throw badRequest('The end time is before the start');
	const mine = await db
		.select({ id: groupPreferences.id })
		.from(groupPreferences)
		.where(and(eq(groupPreferences.groupId, groupId), eq(groupPreferences.userId, userId)));
	if (mine.length >= MAX_PER_MEMBER) throw badRequest(`You can have up to ${MAX_PER_MEMBER} preferences in a group`);
	const id = newId();
	await db.insert(groupPreferences).values({
		id,
		groupId,
		userId,
		kind: input.kind,
		fromDate: input.kind === 'dates' ? input.from! : null,
		toDate: input.kind === 'dates' ? input.to! : null,
		days: input.kind === 'weekly' ? [...new Set(input.days ?? [])].sort() : [],
		windowFrom: input.kind === 'weekly' ? (input.window?.from ?? null) : null,
		windowTo: input.kind === 'weekly' ? (input.window?.to ?? null) : null
	});
	const row = (await db.query.groupPreferences.findFirst({ where: eq(groupPreferences.id, id) }))!;
	return toPreference(row, (await getGroupRow(groupId)).requirement.timezone);
}

/** Removes one of your own preferences. */
export async function removePreference(groupId: string, userId: string, id: string): Promise<void> {
	await requireMember(groupId, userId);
	const [res] = await db
		.delete(groupPreferences)
		.where(and(eq(groupPreferences.id, id), eq(groupPreferences.groupId, groupId), eq(groupPreferences.userId, userId)));
	if (res.affectedRows === 0) throw notFound('Preference not found');
}
