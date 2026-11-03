/**
 * The availability engine: given a group's requirement and every member's busy
 * time, find the slots that work and how each calendar day should be drawn.
 *
 * Pure and synchronous: callers load busy time / holidays and pass them in.
 */
import type {
	Availability,
	AvailabilityDay,
	DateRange,
	DayAvailability,
	Requirement,
	Slot,
	SlotPosition,
	Weekday
} from '#lib/types';
import { clip, isFree, merge, overlaps, subtract, type Span } from './intervals';
import { addDays, eachDate, inRange, minutesOf, startOfDate, weekdayOf, zonedInstant } from './time';
import { slotLabel } from './labels';

export interface MemberBusy {
	userId: string;
	synced: boolean;
	busy: (Span & { manual: boolean })[];
	/** Calendar busy time is only known before this instant (absent = no limit). */
	checkedUntil?: number | null;
}

export interface EngineInput {
	requirement: Requirement;
	/** First and last date (inclusive) to report on. */
	from: string;
	to: string;
	members: MemberBusy[];
	/** Bank holiday name by date. */
	bankHolidays: ReadonlyMap<string, string>;
	/** Resolved school holiday ranges for the group's authority (plus custom ones). */
	schoolHolidays: readonly (DateRange & { name: string })[];
	/** Slots starting before this instant are ignored. */
	now: number;
}

/** A possible slot before anyone's busy time is considered. */
interface Candidate {
	start: number;
	end: number;
	/** The part of each date the slot occupies. */
	pieces: Map<string, Span>;
	/** Duration requirements match any free stretch of this length inside the pieces. */
	minLength: number | null;
}

const ALL_WEEKDAYS: Weekday[] = [0, 1, 2, 3, 4, 5, 6];

// ─── Candidate generation ─────────────────────────────────────────────────

function context(input: EngineInput) {
	const { requirement: req } = input;
	const tz = req.timezone;

	const schoolHoliday = (date: string) => input.schoolHolidays.find((r) => inRange(date, r))?.name ?? null;

	/** Range, specific-dates and school-holiday constraints shared by every length type. */
	const dateAllowed = (date: string): boolean =>
		inRange(date, req.range) &&
		(req.dates.length === 0 || req.dates.some((r) => inRange(date, r))) &&
		(!req.schoolHolidaysOnly || schoolHoliday(date) !== null);

	/** An empty `days` list means any day of the week. */
	const weekdayAllowed = (date: string): boolean =>
		req.days.length === 0 || req.days.includes(weekdayOf(date)) || (req.bankHolidays && input.bankHolidays.has(date));

	const dayWindow = (date: string): Span => {
		const from = req.window ? minutesOf(req.window.from) : 0;
		const to = req.window ? minutesOf(req.window.to, { asEnd: true }) : 24 * 60;
		return { start: zonedInstant(date, from, tz), end: zonedInstant(date, to, tz) };
	};

	const wholeDay = (date: string): Span => ({ start: startOfDate(date, tz), end: startOfDate(addDays(date, 1), tz) });

	return { req, tz, dateAllowed, weekdayAllowed, dayWindow, wholeDay, schoolHoliday };
}

type Ctx = ReturnType<typeof context>;

/** How many days before `from` a slot that still overlaps the range could start. */
function lookback(req: Requirement): number {
	if (req.length.type === 'days') return req.length.count - 1;
	if (req.length.type === 'span') return spanDays(req.length) - 1;
	return 0;
}

function spanDays(l: Extract<Requirement['length'], { type: 'span' }>): number {
	return ((l.endDay - l.startDay + 7) % 7) + 7 * l.weeksLater + 1;
}

function* candidates(ctx: Ctx, from: string, to: string): Generator<Candidate> {
	const { req, dateAllowed, weekdayAllowed, dayWindow, wholeDay, tz } = ctx;
	const length = req.length;

	for (const date of eachDate(addDays(from, -lookback(req)), to)) {
		if (length.type === 'duration') {
			if (!dateAllowed(date) || !weekdayAllowed(date)) continue;
			const window = dayWindow(date);
			yield { ...window, pieces: new Map([[date, window]]), minLength: length.minutes * 60_000 };
		} else if (length.type === 'days') {
			const dates = eachDate(date, addDays(date, length.count - 1));
			if (!dates.every((d) => dateAllowed(d) && weekdayAllowed(d))) continue;
			const pieces = new Map(dates.map((d) => [d, dayWindow(d)] as const));
			yield { start: pieces.get(dates[0])!.start, end: pieces.get(dates[dates.length - 1])!.end, pieces, minLength: null };
		} else {
			if (weekdayOf(date) !== length.startDay) continue;
			const dates = eachDate(date, addDays(date, spanDays(length) - 1));
			if (!dates.every(dateAllowed)) continue;
			const span = {
				start: zonedInstant(date, minutesOf(length.startTime), tz),
				end: zonedInstant(dates[dates.length - 1], minutesOf(length.endTime, { asEnd: true }), tz)
			};
			const pieces = new Map<string, Span>();
			for (const d of dates) {
				const piece = clip(span, wholeDay(d));
				if (piece) pieces.set(d, piece);
			}
			yield { ...span, pieces, minLength: null };
		}
	}
}

// ─── Evaluation ───────────────────────────────────────────────────────────

/** Free stretches of a candidate that satisfy the requirement (empty when it doesn't work). */
function workingSpans(c: Candidate, busy: Span[], now: number): Span[] {
	if (c.minLength === null) {
		return c.start >= now && [...c.pieces.values()].every((p) => isFree(p, busy)) ? [{ start: c.start, end: c.end }] : [];
	}
	const within = { start: Math.max(c.start, now), end: c.end };
	if (within.end - within.start < c.minLength) return [];
	return subtract(within, busy).filter((s) => s.end - s.start >= c.minLength!);
}

function positionFor(dates: string[], date: string): SlotPosition {
	if (dates.length === 1) return 'single';
	if (date === dates[0]) return 'start';
	return date === dates[dates.length - 1] ? 'end' : 'middle';
}

export function computeAvailability(input: EngineInput): Availability {
	const ctx = context(input);
	const synced = input.members.filter((m) => m.synced);
	const everyoneBusy = merge(synced.flatMap((m) => m.busy));
	const busyByMember = synced.map((m) => ({ userId: m.userId, busy: merge(m.busy) }));

	const slots: Slot[] = [];
	const covered = new Map<string, Span[]>(); // date → candidate pieces (any outcome)
	const matched = new Map<string, SlotPosition>();

	for (const c of candidates(ctx, input.from, input.to)) {
		const dates = [...c.pieces.keys()];
		for (const [date, piece] of c.pieces) covered.set(date, [...(covered.get(date) ?? []), piece]);

		for (const span of workingSpans(c, everyoneBusy, input.now)) {
			slots.push({ start: new Date(span.start).toISOString(), end: new Date(span.end).toISOString(), label: slotLabel(span, ctx.req) });
			for (const date of dates) if (!matched.has(date)) matched.set(date, positionFor(dates, date));
		}
	}

	const days: AvailabilityDay[] = eachDate(input.from, input.to).map((date) => {
		const pieces = covered.get(date);
		const holiday = input.bankHolidays.get(date) ?? ctx.schoolHoliday(date);
		if (!pieces) return { date, status: 'hidden', busyMemberIds: [], slotPosition: null, holiday };

		const relevant = merge(pieces);
		const busyMemberIds = busyByMember
			.filter((m) => m.busy.some((b) => relevant.some((p) => overlaps(b, p))))
			.map((m) => m.userId);
		const slotPosition = matched.get(date) ?? null;
		const status = slotPosition ? 'match' : busyMemberIds.length ? 'busy' : 'free';
		return { date, status, busyMemberIds, slotPosition, holiday };
	});

	slots.sort((a, b) => a.start.localeCompare(b.start));

	return {
		from: input.from,
		to: input.to,
		visibleWeekdays: visibleWeekdays(ctx, days, input),
		days,
		slots: dedupe(slots),
		syncedCount: synced.length,
		memberCount: input.members.length,
		pendingMemberIds: input.members.filter((m) => !m.synced).map((m) => m.userId),
		checkedUntil: coverageGaps(input)
	};
}

/** Synced members whose calendars aren't known to the end of the reported range: times past that may clash. */
function coverageGaps(input: EngineInput): { userId: string; until: string }[] {
	const end = startOfDate(addDays(input.to, 1), input.requirement.timezone);
	return input.members
		.filter((m) => m.synced && m.checkedUntil != null && m.checkedUntil < end)
		.map((m) => ({ userId: m.userId, until: new Date(m.checkedUntil!).toISOString() }));
}

/** Weekday columns worth drawing: the requirement's pattern plus any day that's actually in play. */
function visibleWeekdays(ctx: Ctx, days: AvailabilityDay[], input: EngineInput): Weekday[] {
	const { req } = ctx;
	const set = new Set<Weekday>();
	if (req.length.type === 'span') {
		const count = Math.min(spanDays(req.length), 7);
		for (let i = 0; i < count; i++) set.add(((req.length.startDay + i) % 7) as Weekday);
	} else {
		(req.days.length ? req.days : ALL_WEEKDAYS).forEach((d) => set.add(d));
		if (req.bankHolidays) {
			for (const date of input.bankHolidays.keys()) if (inRange(date, { from: input.from, to: input.to })) set.add(weekdayOf(date));
		}
	}
	for (const d of days) if (d.status !== 'hidden') set.add(weekdayOf(d.date));
	return ALL_WEEKDAYS.filter((d) => set.has(d));
}

function dedupe(slots: Slot[]): Slot[] {
	const seen = new Set<string>();
	return slots.filter((s) => {
		const key = `${s.start}|${s.end}`;
		if (seen.has(key)) return false;
		seen.add(key);
		return true;
	});
}

// ─── Single day ───────────────────────────────────────────────────────────

/** Who is busy when on one date, for the day view. Busy times are reported without titles. */
export function computeDay(input: EngineInput, date: string): DayAvailability {
	const ctx = context(input);
	const day = ctx.wholeDay(date);
	const pieces = [...candidates(ctx, date, date)].flatMap((c) => (c.pieces.has(date) ? [c.pieces.get(date)!] : []));
	const relevant = merge(pieces);
	const window = relevant.length ? { start: relevant[0].start, end: relevant[relevant.length - 1].end } : null;

	const members = input.members.map((m) => ({
		userId: m.userId,
		pending: !m.synced,
		busy: m.busy
			.map((b) => ({ span: clip(b, day), manual: b.manual }))
			.filter((b): b is { span: Span; manual: boolean } => b.span !== null)
			.sort((a, b) => a.span.start - b.span.start)
			.map((b) => ({ ...toIso(b.span), manual: b.manual }))
	}));

	const everyoneBusy = merge(input.members.filter((m) => m.synced).flatMap((m) => m.busy));
	const everyoneFree = window ? relevant.flatMap((p) => subtract(p, everyoneBusy)).map(toIso) : [];

	return { date, window: window && toIso(window), members, everyoneFree };
}

const toIso = (s: Span) => ({ start: new Date(s.start).toISOString(), end: new Date(s.end).toISOString() });

