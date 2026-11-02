/**
 * The landing page demo: a made-up group's November 2026, worked out the same
 * way the real availability engine does (simplified to whole days).
 */
export type Mode = 'weekend' | 'evening';

export interface Person {
	id: string;
	name: string;
	/** Days of November 2026 they're busy. */
	busy: number[];
}

export const PEOPLE: Person[] = [
	{ id: 'sam', name: 'Sam', busy: [10, 25] },
	{ id: 'priya', name: 'Priya', busy: [7, 18] },
	{ id: 'tom', name: 'Tom', busy: [20, 26] },
	{ id: 'ana', name: 'Ana', busy: [14, 24] },
	{ id: 'leo', name: 'Leo', busy: [5, 12] },
	{ id: 'jade', name: 'Jade', busy: [21, 3] }
];

/** 1 November 2026 is a Sunday; weekday 0 = Monday. */
export const weekdayOf = (d: number) => (d + 5) % 7;
export const DAYS_IN_MONTH = 30;

export const COLUMNS: Record<Mode, number[]> = { weekend: [4, 5, 6], evening: [1, 2, 3] };
export const COLUMN_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

export type CellKind = 'blank' | 'busy' | 'free' | 'match';

export interface Cell {
	day: number | null;
	kind: CellKind;
	/** Who's busy, for the label. */
	busyNames: string[];
	/** Position in a joined weekend band. */
	band: 'start' | 'middle' | 'end' | 'single' | null;
}

export interface Slot {
	days: number[];
	label: string;
}

const MONTH = 'Nov';

function busyOn(day: number, people: Person[]): string[] {
	return people.filter((p) => p.busy.includes(day)).map((p) => p.name);
}

/** The times that work for everyone included: whole Fri–Sun weekends, or single Tue–Thu evenings. */
export function slots(mode: Mode, people: Person[]): Slot[] {
	const out: Slot[] = [];
	for (let d = 1; d <= DAYS_IN_MONTH; d++) {
		if (mode === 'weekend') {
			if (weekdayOf(d) !== 4 || d + 2 > DAYS_IN_MONTH) continue;
			const days = [d, d + 1, d + 2];
			if (days.every((x) => busyOn(x, people).length === 0)) out.push({ days, label: `Fri ${d} – Sun ${d + 2} ${MONTH}` });
		} else if (COLUMNS.evening.includes(weekdayOf(d)) && busyOn(d, people).length === 0) {
			out.push({ days: [d], label: `${COLUMN_NAMES[weekdayOf(d)].slice(0, 3)} ${d} ${MONTH}, from 18:00` });
		}
	}
	return out;
}

/** Rows of cells for the visible columns, Monday-first weeks. */
export function grid(mode: Mode, people: Person[]): Cell[] {
	const cols = COLUMNS[mode];
	const matched = new Map<number, Cell['band']>();
	for (const s of slots(mode, people)) {
		s.days.forEach((d, i) => matched.set(d, s.days.length === 1 ? 'single' : i === 0 ? 'start' : i === s.days.length - 1 ? 'end' : 'middle'));
	}
	const cells: Cell[] = [];
	// Week rows: the Monday on or before the 1st is 26 October (day -5).
	for (let monday = -5; monday <= DAYS_IN_MONTH; monday += 7) {
		const row = cols.map((c) => monday + c);
		if (row.every((d) => d < 1 || d > DAYS_IN_MONTH)) continue;
		for (const d of row) {
			if (d < 1 || d > DAYS_IN_MONTH) {
				cells.push({ day: null, kind: 'blank', busyNames: [], band: null });
				continue;
			}
			const names = busyOn(d, people);
			const band = matched.get(d) ?? null;
			cells.push({ day: d, kind: band ? 'match' : names.length ? 'busy' : 'free', busyNames: names, band });
		}
	}
	return cells;
}

export function busyLabel(names: string[]): string {
	if (!names.length) return '';
	return names.length === 1 ? `${names[0]} busy` : `${names.length} busy`;
}
