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

export function myResponse(p: Proposal, meId: string): ResponseKind | null {
	return p.responses.find((r) => r.userId === meId)?.response ?? null;
}
