/**
 * How far busy time is kept: from a week back to a horizon at least three years
 * ahead, further when one of the user's groups plans past that (up to ten years),
 * and what each sync reads to keep it that way.
 */
import { and, eq, isNull, lt, ne, or } from 'drizzle-orm';
import { db } from '../db';
import { calendarConnections, groupMembers, groups } from '../db/schema';
import { log } from '../log';

const DAY_MS = 24 * 60 * 60 * 1000;
/** Busy time is kept from this far back (for today's view). */
const HISTORY_DAYS = 7;
/** Everyone's calendars are covered at least this far ahead… */
const DEFAULT_HORIZON_DAYS = 3 * 365;
/** …and as far as their groups plan, up to this. */
const MAX_HORIZON_DAYS = 10 * 365;
/** Slots near the end of a group's range still look at busy time a little past it. */
const RANGE_SLACK_DAYS = 15;
/** Coverage grows in steps, so the rolling horizon costs one extra fetch a month, not one a day. */
const EXTEND_STEP_DAYS = 30;
/** Calendars without change tracking: re-read this far ahead on every sync… */
const NEAR_DAYS = 183;
/** …and the rest of their range this often. */
const FAR_EVERY_MS = DAY_MS;

/**
 * How far ahead a user's busy time should be known: three years, or the end of
 * the furthest-off range any of their groups is searching, up to ten years.
 */
export async function horizonFor(userId: string, now = Date.now()): Promise<Date> {
	const rows = await db
		.select({ requirement: groups.requirement })
		.from(groupMembers)
		.innerJoin(groups, eq(groups.id, groupMembers.groupId))
		.where(eq(groupMembers.userId, userId));
	const ends = rows.map((r) => Date.parse(`${r.requirement.range.to}T00:00:00Z`) + RANGE_SLACK_DAYS * DAY_MS).filter(Number.isFinite);
	const wanted = Math.max(now + DEFAULT_HORIZON_DAYS * DAY_MS, ...ends);
	return new Date(Math.min(wanted, now + MAX_HORIZON_DAYS * DAY_MS));
}

/**
 * Call when groups may now plan further ahead (a group's range changed, or
 * someone joined one): server connections that don't reach the users' new
 * horizon are made due, so the next background run extends them.
 */
export async function refreshCoverage(userIds: string[]): Promise<void> {
	try {
		for (const userId of new Set(userIds)) {
			const horizon = await horizonFor(userId);
			await db
				.update(calendarConnections)
				.set({ nextSyncAt: null })
				.where(
					and(
						eq(calendarConnections.userId, userId),
						ne(calendarConnections.provider, 'device'),
						eq(calendarConnections.status, 'ok'),
						or(isNull(calendarConnections.coveredUntil), lt(calendarConnections.coveredUntil, horizon))
					)
				);
		}
	} catch (err) {
		// Only hurries a sync along: the regular one still extends coverage.
		log.warn('calendars', 'refreshCoverage failed', { err });
	}
}

/** What one sync of a connection reads, given what's stored and the user's horizon. */
export interface Coverage {
	now: Date;
	/** Start of the stored range. */
	from: Date;
	/** How far ahead busy time is stored now (null = never synced). */
	covered: Date | null;
	/** How far ahead it will be stored after this sync. */
	until: Date;
	/** End of the near tier for calendars without change tracking. */
	nearEnd: Date;
	/** Whether those calendars' far tier is re-read this sync. */
	farDue: boolean;
}

export function planCoverage(row: { coveredUntil: Date | null; farSyncedAt: Date | null }, horizon: Date, now = Date.now()): Coverage {
	const covered = row.coveredUntil;
	const step = EXTEND_STEP_DAYS * DAY_MS;
	const stepped = new Date(horizon.getTime() + step);
	// Grow past the horizon by a step; shrink back once a far-off group has gone.
	const resize = !covered || covered < horizon || covered.getTime() > stepped.getTime() + step;
	const until = resize ? stepped : covered;
	return {
		now: new Date(now),
		from: new Date(now - HISTORY_DAYS * DAY_MS),
		covered,
		until,
		nearEnd: new Date(Math.min(now + NEAR_DAYS * DAY_MS, until.getTime())),
		farDue: resize || !row.farSyncedAt || now - row.farSyncedAt.getTime() >= FAR_EVERY_MS
	};
}
