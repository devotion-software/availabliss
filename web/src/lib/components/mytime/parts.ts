/**
 * "My time" day model: every busy interval placed at its exact start and end
 * within each day. Overlapping intervals share the column side by side;
 * intervals covering whole days sit in the all-day row.
 */
import type { BusyInterval, ManualBlock } from '#lib/types';
import { addDays, dayKeyIn, minutesIntoDay, timeIn, zonedToIso } from '#lib/client/dates';
import { scheduleLead } from './repeat';
import { formatDayKey } from '#lib/client/format';

export const DAY_MINUTES = 24 * 60;
/** Drag-to-block snaps to quarter hours. */
export const SNAP_MINUTES = 15;
/** Tapping an empty slot suggests an hour. */
export const DEFAULT_BLOCK_MINUTES = 60;

export interface PlacedInterval {
	interval: BusyInterval;
	day: string;
	/** Minutes from the start of `day`, clamped to the day. */
	from: number;
	to: number;
	/** Side-by-side position among overlapping intervals. */
	lane: number;
	lanes: number;
	/** The interval carries on from the previous day / into the next. */
	continuesBefore: boolean;
	continuesAfter: boolean;
}

export interface DaysLayout {
	days: string[];
	/** Intervals that cover whole days, per day. */
	allDay: Map<string, BusyInterval[]>;
	/** Timed intervals per day, with lanes. */
	timed: Map<string, PlacedInterval[]>;
}

/** Length of `day` in minutes (1380 / 1500 on DST changes). */
export function dayLength(day: string, timeZone: string): number {
	return minutesIntoDay(zonedToIso(addDays(day, 1), '00:00', timeZone), day, timeZone);
}

/** Assigns lanes so overlapping intervals sit side by side; each overlap cluster shares a lane count. */
export function assignLanes<T extends { from: number; to: number }>(items: T[]): (T & { lane: number; lanes: number })[] {
	const sorted = [...items].sort((a, b) => a.from - b.from || b.to - a.to);
	const out: (T & { lane: number; lanes: number })[] = [];
	let cluster: (T & { lane: number; lanes: number })[] = [];
	let laneEnds: number[] = [];
	let clusterEnd = -Infinity;

	const flush = () => {
		for (const item of cluster) item.lanes = laneEnds.length;
		out.push(...cluster);
		cluster = [];
		laneEnds = [];
	};

	for (const item of sorted) {
		if (item.from >= clusterEnd) flush();
		let lane = laneEnds.findIndex((end) => end <= item.from);
		if (lane === -1) lane = laneEnds.push(item.to) - 1;
		else laneEnds[lane] = item.to;
		cluster.push({ ...item, lane, lanes: 1 });
		clusterEnd = Math.max(clusterEnd, item.to);
	}
	flush();
	return out;
}

/**
 * Lays out busy time for any run of days (the list's days, or one day in the day sheet).
 * Intervals are bucketed by the days they touch first, so long ranges stay cheap.
 */
export function layoutDays(days: readonly string[], intervals: readonly BusyInterval[], timeZone: string): DaysLayout {
	const wanted = new Set(days);
	const byDay = new Map<string, BusyInterval[]>(days.map((d) => [d, []]));
	for (const interval of intervals) {
		const first = dayKeyIn(interval.start, timeZone);
		const last = dayKeyIn(interval.end, timeZone);
		for (let d = first; d <= last; d = addDays(d, 1)) if (wanted.has(d)) byDay.get(d)!.push(interval);
	}

	const allDay = new Map<string, BusyInterval[]>(days.map((d) => [d, []]));
	const timed = new Map<string, PlacedInterval[]>();
	for (const day of days) {
		const length = dayLength(day, timeZone);
		const pieces: Omit<PlacedInterval, 'lane' | 'lanes'>[] = [];
		for (const interval of byDay.get(day)!) {
			const start = minutesIntoDay(interval.start, day, timeZone);
			const end = minutesIntoDay(interval.end, day, timeZone);
			if (end <= 0 || start >= length) continue;
			// Ending at 23:59 or later reaches midnight (some calendars end all-day events at 23:59:59).
			if (start <= 0 && end >= length - 1) {
				allDay.get(day)!.push(interval);
				continue;
			}
			// Scale DST days onto the 24-hour axis so 23:00 still lines up with 23:00.
			const scale = DAY_MINUTES / length;
			pieces.push({
				interval,
				day,
				from: Math.max(0, start) * scale,
				to: Math.min(length, end) * scale,
				continuesBefore: start < 0,
				continuesAfter: end > length
			});
		}
		timed.set(day, assignLanes(pieces));
	}
	return { days: [...days], allDay, timed };
}

/** A drag between two points, snapped outwards to quarter hours, at least one step long. */
export function selectionRange(a: number, b: number, step = SNAP_MINUTES): { from: number; to: number } {
	const lo = Math.max(0, Math.min(a, b));
	const hi = Math.min(DAY_MINUTES, Math.max(a, b));
	const from = Math.floor(lo / step) * step;
	const to = Math.max(from + step, Math.ceil(hi / step) * step);
	return { from: Math.min(from, DAY_MINUTES - step), to: Math.min(to, DAY_MINUTES) };
}

/** The range a tap on an empty slot suggests: the hour it falls in. */
export function tapRange(at: number): { from: number; to: number } {
	const from = Math.min(Math.floor(at / 60) * 60, DAY_MINUTES - DEFAULT_BLOCK_MINUTES);
	return { from, to: from + DEFAULT_BLOCK_MINUTES };
}

/** "09:15" for minutes since midnight; 1440 is "24:00". */
export function clock(minutes: number): string {
	const m = Math.round(minutes);
	return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
}

/** Instants for a minutes range on `day`; 24:00 is the next midnight. */
export function rangeToIso(day: string, from: number, to: number, timeZone: string): { start: string; end: string } {
	const at = (m: number) => (m >= DAY_MINUTES ? zonedToIso(addDays(day, 1), '00:00', timeZone) : zonedToIso(day, clock(m), timeZone));
	return { start: at(from), end: at(to) };
}

/**
 * "Every Tuesday · 19:00 – 21:30", "Monthly on the 3rd Monday · All day · until Sun 20 Dec",
 * "Sat 21 Nov · 09:00 – 12:00" (one-offs).
 */
export function blockSummary(block: ManualBlock, timeZone: string): string {
	const day = dayKeyIn(block.start, timeZone);
	const when = block.allDay ? 'All day' : `${timeIn(block.start, timeZone)} – ${timeIn(block.end, timeZone)}`;
	const lead = scheduleLead(block.repeat, day);
	if (!lead) return `${formatDayKey(day)} · ${when}`;
	return `${lead} · ${when}${block.until ? ` · until ${formatDayKey(block.until)}` : ''}`;
}
