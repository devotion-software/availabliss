import type { GroupSummary, SlotNoun } from '#lib/types';
import type { PillTone } from '#lib/components/ui/StatusPill.svelte';
import { createFormatter } from '#lib/client/format';

const plural = (n: number, w: string) => `${n} ${w}${n === 1 ? '' : 's'}`;

/** Badge on the groups list: tone + text, from `GroupSummary.status`. */
export function groupBadge(g: GroupSummary, timeZone: string): { tone: PillTone; text: string } {
	const s = g.status;
	switch (s.kind) {
		case 'matches':
			return { tone: 'indigo', text: matchesText(s.count, s.noun) };
		case 'needs_reply':
			return { tone: 'apricot', text: 'Needs your reply' };
		case 'confirmed':
			return { tone: 'mint', text: createFormatter(timeZone).day(s.start) };
		default:
			return { tone: 'neutral', text: 'Finding times' };
	}
}

/** Second line in the sidebar: "2 weekends work", "Needs your reply", "3 of 10 to sync". */
export function groupStatusText(g: GroupSummary): string {
	const left = g.memberCount - g.syncedCount;
	if (g.status.kind === 'needs_reply') return 'Needs your reply';
	if (g.status.kind === 'matches') return matchesText(g.status.count, g.status.noun);
	if (g.status.kind === 'confirmed') return 'Confirmed';
	if (left > 0) return `${left} of ${g.memberCount} to sync`;
	return 'Finding times';
}

/** "2 weekends work", "1 evening works". */
const matchesText = (count: number, noun: SlotNoun) => `${plural(count, noun === 'slot' ? 'time' : noun)} work${count === 1 ? 's' : ''}`;

export function syncText(g: Pick<GroupSummary, 'memberCount' | 'syncedCount'>): string {
	const left = g.memberCount - g.syncedCount;
	return left <= 0 ? 'All synced' : `${left} of ${g.memberCount} to sync`;
}
