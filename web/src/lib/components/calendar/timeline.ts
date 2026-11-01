/** Positioning helpers for the hour-based views (week columns, day rows). */
import type { Interval } from '#lib/types';
import { minutesIntoDay, minutesToTime } from '#lib/client/dates';

export interface Axis {
	/** Minutes from midnight. */
	start: number;
	end: number;
}

/** 08:00–24:00 by default, stretched earlier if anything starts before 8. */
export function axisFor(date: string, intervals: readonly Interval[], timeZone: string): Axis {
	let start = 8 * 60;
	for (const i of intervals) {
		const m = minutesIntoDay(i.start, date, timeZone);
		if (m < start) start = Math.max(0, Math.floor(m / 60) * 60);
	}
	return { start, end: 24 * 60 };
}

export const mergeAxes = (axes: readonly Axis[]): Axis =>
	axes.length ? { start: Math.min(...axes.map((a) => a.start)), end: Math.max(...axes.map((a) => a.end)) } : { start: 480, end: 1440 };

/** An interval as clamped [from, to] minutes on the axis, or null when it's outside. */
export function clampToAxis(i: Interval, date: string, axis: Axis, timeZone: string): { from: number; to: number } | null {
	const from = Math.max(axis.start, minutesIntoDay(i.start, date, timeZone));
	const to = Math.min(axis.end, minutesIntoDay(i.end, date, timeZone));
	return to > from ? { from, to } : null;
}

export const pct = (minutes: number, axis: Axis) => ((minutes - axis.start) / (axis.end - axis.start)) * 100;

/** Hour labels every `step` hours: "08", "12", … "24". */
export function hourTicks(axis: Axis, step = 4): { label: string; at: number }[] {
	const ticks = [];
	for (let m = Math.ceil(axis.start / 60 / step) * step * 60; m <= axis.end; m += step * 60) {
		ticks.push({ label: String(m / 60).padStart(2, '0'), at: pct(m, axis) });
	}
	return ticks;
}

/** "18:00 – late", "10:00 – 14:30" */
export const spanText = (from: number, to: number) => `${minutesToTime(from)} – ${to >= 1439 ? 'late' : minutesToTime(to)}`;
