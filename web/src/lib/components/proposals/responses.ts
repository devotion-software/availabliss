import type { Group, Proposal, ResponseKind } from '#lib/types';
import type { PillTone } from '#lib/components/ui/StatusPill.svelte';

export const RESPONSE_PILL: Record<ResponseKind, { tone: PillTone; text: string }> = {
	accepted: { tone: 'mint', text: 'Accepted' },
	maybe: { tone: 'indigo', text: 'Maybe' },
	pending: { tone: 'apricot', text: 'Waiting' },
	declined: { tone: 'neutral', text: "Can't make it" }
};

export function acceptedCount(p: Proposal): number {
	return p.responses.filter((r) => r.response === 'accepted').length;
}

/** Soft yeses ("if I must"): counted apart from the firm ones, and not enough to confirm. */
export function maybeCount(p: Proposal): number {
	return p.responses.filter((r) => r.response === 'maybe').length;
}

/** " · 1 maybe" to follow a count of firm yeses, or nothing when there are none. */
function maybeSuffix(p: Proposal): string {
	const maybe = maybeCount(p);
	return maybe ? ` · ${maybe} maybe` : '';
}

/** An open proposal's replies: "3 of 5 accepted", or "3 of 5 accepted · 1 maybe". */
export function acceptedText(p: Proposal, total: number): string {
	return `${acceptedCount(p)} of ${total} accepted${maybeSuffix(p)}`;
}

/** Total people asked: responses if present, else the group's size. */
export function askedCount(p: Proposal, group?: Pick<Group, 'members'>): number {
	return p.responses.length || group?.members.length || 0;
}

/** Everyone it was put to accepted; false once confirmed anyway with someone who's a maybe, can't make it or never replied. */
export function everyoneIn(p: Proposal): boolean {
	return p.responses.every((r) => r.response === 'accepted');
}

/** A confirmed plan's status: "Everyone's in", or "It's on · 3 of 4 coming" (" · 1 maybe"). */
export function confirmedStatus(p: Proposal): string {
	return everyoneIn(p) ? "Everyone's in" : `It's on · ${acceptedCount(p)} of ${p.responses.length} coming${maybeSuffix(p)}`;
}

/** "Priya", "Priya and Tom", "Priya, Tom and Jo". */
export function listNames(names: string[]): string {
	if (names.length <= 1) return names[0] ?? '';
	return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
}

export function myResponse(p: Proposal, meId: string): ResponseKind | null {
	const r = p.responses.find((x) => x.userId === meId);
	return r ? knownResponse(r.response) : null;
}

/** A reply as the UI shows it: anything this build doesn't know yet reads as waiting, as the API asks. */
export function knownResponse(response: string | null | undefined): ResponseKind {
	return response && Object.hasOwn(RESPONSE_PILL, response) ? (response as ResponseKind) : 'pending';
}
