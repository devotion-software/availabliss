/** Manual busy blocks ("My time"), including daily, weekly, monthly and yearly repeats. */
import { and, desc, eq } from 'drizzle-orm';
import type { ManualBlock, Repeat } from '#lib/types';
import { db } from './db';
import { manualBlocks } from './db/schema';
import { badRequest, notFound } from './http';
import { newId } from './ids';
import { addDays, DAY, dateOf, daysBetween, wallClock, weekdayOf, zonedInstant } from './availability/time';
import type { Span } from './availability/intervals';

type BlockRow = typeof manualBlocks.$inferSelect;

export const toManualBlock = (row: BlockRow): ManualBlock => ({
	id: row.id,
	start: row.start.toISOString(),
	end: row.end.toISOString(),
	allDay: row.allDay,
	repeat: row.repeat,
	until: row.until,
	note: row.note
});

/** Repeats that step a fixed number of days. */
const STEP_DAYS: Partial<Record<Repeat, number>> = { daily: 1, weekly: 7, fortnightly: 14 };

const ymd = (date: string) => date.split('-').map(Number) as [number, number, number];
const daysInMonth = (y: number, m: number) => new Date(Date.UTC(y, m, 0)).getUTCDate();
const iso = (y: number, m: number, d: number) => `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
/** Month `m` (1–12) of year `y`, `n` months on. */
function addMonths(y: number, m: number, n: number): [number, number] {
	const index = y * 12 + (m - 1) + n;
	return [Math.floor(index / 12), (index % 12) + 1];
}

/**
 * The local dates a repeat falls on, in order, starting from its first date, from the first
 * one on or after `fromDate` (roughly: it may start a little before). Stops at `until`.
 */
export function* occurrenceDates(first: string, repeat: Repeat, fromDate: string, until: string | null): Generator<string> {
	const done = (date: string) => until !== null && date > until;
	if (repeat === 'none') {
		if (!done(first)) yield first;
		return;
	}
	const step = STEP_DAYS[repeat];
	if (step) {
		const k = Math.max(0, Math.floor(daysBetween(first, fromDate) / step) - 1);
		for (let date = addDays(first, k * step); !done(date); date = addDays(date, step)) yield date;
		return;
	}
	if (repeat === 'weekdays') {
		const k = Math.max(0, daysBetween(first, fromDate) - 7);
		for (let date = addDays(first, k); !done(date); date = addDays(date, 1)) {
			if (weekdayOf(date) < 5) yield date; // Mon–Fri (0 = Monday)
		}
		return;
	}
	const [fy, fm, fd] = ymd(first);
	const [ty, tm] = ymd(fromDate);
	if (repeat === 'yearly') {
		for (let y = Math.max(fy, ty - 1); ; y++) {
			if (fd > daysInMonth(y, fm)) continue; // 29 Feb: leap years only
			const date = iso(y, fm, fd);
			if (done(date)) return;
			yield date;
		}
	}
	// Monthly: walk month by month from just before the range.
	const skip = Math.max(0, (ty - fy) * 12 + (tm - fm) - 1);
	const nth = Math.ceil(fd / 7);
	const last = fd + 7 > daysInMonth(fy, fm);
	const weekday = new Date(Date.UTC(fy, fm - 1, fd)).getUTCDay();
	for (let n = skip; ; n++) {
		const [y, m] = addMonths(fy, fm, n);
		let day: number | null;
		if (repeat === 'monthly') {
			day = fd <= daysInMonth(y, m) ? fd : null; // the 31st: months that have one
		} else {
			const firstWeekday = new Date(Date.UTC(y, m - 1, 1)).getUTCDay();
			const firstMatch = 1 + ((weekday - firstWeekday + 7) % 7);
			if (last) {
				day = firstMatch + 7 * Math.floor((daysInMonth(y, m) - firstMatch) / 7);
			} else {
				const d = firstMatch + 7 * (nth - 1);
				day = d <= daysInMonth(y, m) ? d : null;
			}
		}
		if (day === null) continue;
		const date = iso(y, m, day);
		if (done(date)) return;
		yield date;
	}
}

/**
 * Occurrences of a block overlapping `[from, to)`. Repeats keep the same local
 * wall-clock time in `timeZone`, so they don't drift across DST changes.
 */
export function expandBlock(
	block: Pick<BlockRow, 'start' | 'end' | 'repeat' | 'until'>,
	from: number,
	to: number,
	timeZone: string
): Span[] {
	const start = block.start.getTime();
	const duration = block.end.getTime() - start;
	if (block.repeat === 'none') return start < to && start + duration > from ? [{ start, end: start + duration }] : [];

	const firstDate = dateOf(start, timeZone);
	const w = wallClock(start, timeZone);
	const minutes = w.hour * 60 + w.minute;
	// The end keeps its own local date and time too: a fixed duration would put it
	// an hour out on an occurrence that spans a DST change.
	const endDays = daysBetween(firstDate, dateOf(block.end.getTime(), timeZone));
	const we = wallClock(block.end.getTime(), timeZone);
	const endMinutes = we.hour * 60 + we.minute;
	// Start looking a little before the range so an occurrence running into it is caught.
	const fromDate = dateOf(Math.max(start, from - duration - DAY), timeZone);
	const out: Span[] = [];
	for (const date of occurrenceDates(firstDate, block.repeat, fromDate < firstDate ? firstDate : fromDate, block.until)) {
		const s = zonedInstant(date, minutes, timeZone);
		if (s >= to) break;
		if (s < start) continue;
		const local = zonedInstant(addDays(date, endDays), endMinutes, timeZone);
		const e = local > s ? local : s + duration;
		if (e > from) out.push({ start: s, end: e });
	}
	return out;
}

export async function listBlocks(userId: string): Promise<ManualBlock[]> {
	const rows = await db.select().from(manualBlocks).where(eq(manualBlocks.userId, userId)).orderBy(desc(manualBlocks.start));
	return rows.map(toManualBlock);
}

interface BlockInput {
	start: string;
	end: string;
	allDay: boolean;
	repeat: Repeat;
	until: string | null;
	note: string | null;
}

/** API strings → column values. */
function toColumns({ start, end, ...rest }: Partial<BlockInput>): Partial<typeof manualBlocks.$inferInsert> {
	return { ...rest, ...(start && { start: new Date(start) }), ...(end && { end: new Date(end) }) };
}

export async function createBlock(userId: string, input: BlockInput): Promise<ManualBlock> {
	const id = newId();
	await db.insert(manualBlocks).values({ id, userId, ...toColumns(input), start: new Date(input.start), end: new Date(input.end) });
	return getBlock(userId, id);
}

export async function getBlock(userId: string, id: string): Promise<ManualBlock> {
	const row = await db.query.manualBlocks.findFirst({ where: and(eq(manualBlocks.id, id), eq(manualBlocks.userId, userId)) });
	if (!row) throw notFound('Block not found');
	return toManualBlock(row);
}

export async function updateBlock(userId: string, id: string, patch: Partial<BlockInput>): Promise<ManualBlock> {
	const current = await getBlock(userId, id);
	if (new Date(patch.end ?? current.end) <= new Date(patch.start ?? current.start)) {
		throw badRequest('Ends before it starts');
	}
	await db
		.update(manualBlocks)
		.set(toColumns(patch))
		.where(and(eq(manualBlocks.id, id), eq(manualBlocks.userId, userId)));
	return getBlock(userId, id);
}

export async function deleteBlock(userId: string, id: string): Promise<void> {
	await getBlock(userId, id);
	await db.delete(manualBlocks).where(and(eq(manualBlocks.id, id), eq(manualBlocks.userId, userId)));
}

