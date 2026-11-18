/**
 * Pro: a "best date" score for each time that works. It counts who'd prefer it, giving more weight
 * to people who've compromised in the group's recent plans (couldn't make one that went ahead, or
 * only made it as a maybe), so the same person doesn't always lose out; and it nudges towards
 * times with enough notice to organise.
 */
import type { Slot } from '#lib/types';

const DAY = 86_400_000;
/** Each past compromise adds this much to the weight of that person's preferences. */
const PER_COMPROMISE = 0.5;
/** Compromises stop adding weight after this many (one bad run shouldn't swamp everything). */
const MAX_COMPROMISES = 4;

export interface ScoredSlot extends Slot {
	/** Higher is better; only set for Pro. */
	score?: number;
	/** People this time suits who've compromised in recent plans ("good for Ana, who missed the last one"). */
	fairFor?: string[];
}

/** A small bonus for 3–21 days' notice, less for very soon or far off. */
function noticeBonus(start: number, now: number): number {
	const days = (start - now) / DAY;
	if (days < 1) return -0.5;
	if (days < 3) return 0;
	if (days <= 21) return 0.3;
	return 0.1;
}

export function scoreSlots(slots: readonly Slot[], compromises: ReadonlyMap<string, number>, now: number): ScoredSlot[] {
	return slots.map((slot) => {
		const who = slot.preferredBy ?? [];
		const weight = who.reduce((sum, id) => sum + 1 + PER_COMPROMISE * Math.min(compromises.get(id) ?? 0, MAX_COMPROMISES), 0);
		const fairFor = who.filter((id) => (compromises.get(id) ?? 0) > 0).sort((a, b) => (compromises.get(b) ?? 0) - (compromises.get(a) ?? 0));
		return { ...slot, score: Math.round((weight + noticeBonus(Date.parse(slot.start), now)) * 100) / 100, fairFor };
	});
}
