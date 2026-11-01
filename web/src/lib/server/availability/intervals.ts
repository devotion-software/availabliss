/** Half-open time intervals `[start, end)` in epoch milliseconds. */
export interface Span {
	start: number;
	end: number;
}

export const overlaps = (a: Span, b: Span) => a.start < b.end && b.start < a.end;

/** Sorts and merges overlapping or touching intervals. */
export function merge<T extends Span>(spans: readonly T[]): Span[] {
	const sorted = spans.filter((s) => s.end > s.start).sort((a, b) => a.start - b.start);
	const out: Span[] = [];
	for (const s of sorted) {
		const last = out[out.length - 1];
		if (last && s.start <= last.end) last.end = Math.max(last.end, s.end);
		else out.push({ start: s.start, end: s.end });
	}
	return out;
}

/** Parts of `within` not covered by `busy` (which must be merged). */
export function subtract(within: Span, busy: readonly Span[]): Span[] {
	const out: Span[] = [];
	let cursor = within.start;
	for (const b of busy) {
		if (b.end <= cursor) continue;
		if (b.start >= within.end) break;
		if (b.start > cursor) out.push({ start: cursor, end: b.start });
		cursor = Math.max(cursor, b.end);
	}
	if (cursor < within.end) out.push({ start: cursor, end: within.end });
	return out;
}

/** True when nothing in the merged `busy` list overlaps `span`. */
export function isFree(span: Span, busy: readonly Span[]): boolean {
	return !busy.some((b) => overlaps(b, span));
}

export function clip(span: Span, to: Span): Span | null {
	const start = Math.max(span.start, to.start);
	const end = Math.min(span.end, to.end);
	return end > start ? { start, end } : null;
}
