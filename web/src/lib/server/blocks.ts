/** Manual busy blocks ("My time"), including weekly / fortnightly repeats. */
import { and, desc, eq } from 'drizzle-orm';
import type { ManualBlock, Repeat } from '#lib/types';
import { db } from './db';
import { manualBlocks } from './db/schema';
import { badRequest, notFound } from './http';
import { newId } from './ids';
import { addDays, DAY, dateOf, daysBetween, wallClock, zonedInstant } from './availability/time';
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

const STEP_DAYS: Record<Exclude<Repeat, 'none'>, number> = { weekly: 7, fortnightly: 14 };

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

	const step = STEP_DAYS[block.repeat];
	const firstDate = dateOf(start, timeZone);
	const w = wallClock(start, timeZone);
	const minutes = w.hour * 60 + w.minute;
	// The end keeps its own local date and time too: a fixed duration would put it
	// an hour out on an occurrence that spans a DST change.
	const endDays = daysBetween(firstDate, dateOf(block.end.getTime(), timeZone));
	const we = wallClock(block.end.getTime(), timeZone);
	const endMinutes = we.hour * 60 + we.minute;
	const out: Span[] = [];
	// Jump close to the range instead of walking every occurrence since the first.
	let k = Math.max(0, Math.floor((from - duration - start) / (step * DAY)) - 1);
	for (;; k++) {
		const date = addDays(firstDate, k * step);
		if (block.until && date > block.until) break;
		const s = zonedInstant(date, minutes, timeZone);
		if (s >= to) break;
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

