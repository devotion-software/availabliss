/**
 * "My time" grid model: each day is split into morning / afternoon / evening,
 * and every cell knows what makes it busy (a calendar event or your own block).
 */
import type { BusyInterval, ManualBlock } from '#lib/types';
import { addDays, dayKeyIn, eachDay, overlaps, timeIn, weekdayOf, zonedToIso } from '#lib/client/dates';
import { WEEKDAY_NAMES } from '#lib/client/requirements';
import { formatDayKey } from '#lib/client/format';

export type PartId = 'morning' | 'afternoon' | 'evening';

export const PARTS: readonly { id: PartId; label: string; from: string; to: string }[] = [
	{ id: 'morning', label: 'Morning', from: '06:00', to: '12:00' },
	{ id: 'afternoon', label: 'Afternoon', from: '12:00', to: '18:00' },
	{ id: 'evening', label: 'Evening', from: '18:00', to: '24:00' }
];

/** Instants for one part of a day; "24:00" is the next midnight. */
export function partRange(day: string, part: (typeof PARTS)[number], timeZone: string) {
	const end = part.to === '24:00' ? zonedToIso(addDays(day, 1), '00:00', timeZone) : zonedToIso(day, part.to, timeZone);
	return { start: zonedToIso(day, part.from, timeZone), end };
}

export interface Cell {
	day: string;
	part: (typeof PARTS)[number];
	start: string;
	end: string;
	calendar: BusyInterval | null;
	manual: BusyInterval | null;
}

export function weekCells(weekStart: string, intervals: readonly BusyInterval[], timeZone: string): { day: string; cells: Cell[] }[] {
	return eachDay(weekStart, addDays(weekStart, 6)).map((day) => ({
		day,
		cells: PARTS.map((part) => {
			const { start, end } = partRange(day, part, timeZone);
			const hits = intervals.filter((i) => overlaps(i.start, i.end, start, end));
			return {
				day,
				part,
				start,
				end,
				calendar: hits.find((i) => i.source === 'calendar') ?? null,
				manual: hits.find((i) => i.source === 'manual') ?? null
			};
		})
	}));
}

const sameInstant = (a: string, b: string) => new Date(a).getTime() === new Date(b).getTime();

/** A one-off block that covers exactly this cell, so tapping it again can remove it. */
export function toggleableBlock(cell: Cell, blocks: readonly ManualBlock[]): ManualBlock | null {
	const block = blocks.find((b) => b.id === cell.manual?.blockId);
	if (!block || block.repeat !== 'none' || block.allDay) return null;
	return sameInstant(block.start, cell.start) && sameInstant(block.end, cell.end) ? block : null;
}

/** "Every Tuesday · 19:00 – 21:30", "Every other Sat · All day", "Sat 21 Nov · Morning-ish times". */
export function blockSummary(block: ManualBlock, timeZone: string): string {
	const day = dayKeyIn(block.start, timeZone);
	const when = block.allDay ? 'All day' : `${timeIn(block.start, timeZone)} – ${timeIn(block.end, timeZone)}`;
	const weekday = WEEKDAY_NAMES[weekdayOf(day)];
	const until = block.until ? ` until ${formatDayKey(block.until)}` : '';
	if (block.repeat === 'weekly') return `Every ${weekday} · ${when}${until}`;
	if (block.repeat === 'fortnightly') return `Every other ${weekday} · ${when}${until}`;
	return `${formatDayKey(day)} · ${when}`;
}
