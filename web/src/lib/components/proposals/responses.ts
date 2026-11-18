import type { Group, Proposal, ResponseKind } from '#lib/types';
import type { PillTone } from '#lib/components/ui/StatusPill.svelte';

export const RESPONSE_PILL: Record<ResponseKind, { tone: PillTone; text: string }> = {
	accepted: { tone: 'mint', text: 'Accepted' },
	pending: { tone: 'apricot', text: 'Waiting' },
	declined: { tone: 'neutral', text: "Can't make it" }
};

export function acceptedCount(p: Proposal): number {
	return p.responses.filter((r) => r.response === 'accepted').length;
}

/** Total people asked: responses if present, else the group's size. */
export function askedCount(p: Proposal, group?: Pick<Group, 'members'>): number {
	return p.responses.length || group?.members.length || 0;
}

/** Everyone it was put to accepted; false once confirmed anyway with someone who can't make it or never replied. */
export function everyoneIn(p: Proposal): boolean {
	return p.responses.every((r) => r.response === 'accepted');
}

/** A confirmed plan's status: "Everyone's in", or "It's on · 3 of 4 coming". */
export function confirmedStatus(p: Proposal): string {
	return everyoneIn(p) ? "Everyone's in" : `It's on · ${acceptedCount(p)} of ${p.responses.length} coming`;
}

/** "Priya", "Priya and Tom", "Priya, Tom and Jo". */
export function listNames(names: string[]): string {
	if (names.length <= 1) return names[0] ?? '';
	return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
}

export function myResponse(p: Proposal, meId: string): ResponseKind | null {
	return p.responses.find((r) => r.userId === meId)?.response ?? null;
}
