/** Ranking and wording for the group's best options. */
import type { Member, Slot } from '#lib/types';
import { firstName, listNames } from '#lib/client/format';

/**
 * Fewer people missing first, then the Pro "best date" score (higher first, when there is one),
 * then how many people's preferences it fits, then soonest.
 */
export function rankSlots<T extends Slot>(slots: readonly T[]): T[] {
	return [...slots].sort(
		(a, b) =>
			(a.missingMemberIds?.length ?? 0) - (b.missingMemberIds?.length ?? 0) ||
			(b.score ?? -Infinity) - (a.score ?? -Infinity) ||
			(b.preferredBy?.length ?? 0) - (a.preferredBy?.length ?? 0) ||
			a.start.localeCompare(b.start)
	);
}

const names = (ids: readonly string[], members: Map<string, Member>) => ids.map((id) => firstName(members.get(id)?.name ?? 'Someone'));

/** "Ana can't make it", "Ana and Tom can't make it", "3 people can't make it". */
export function missingLine(ids: readonly string[], members: Map<string, Member>): string | null {
	if (!ids.length) return null;
	return `${ids.length > 2 ? `${ids.length} people` : listNames(names(ids, members), 2)} can't make it`;
}

/** "Fair for Ana, who missed out last time", "Fair for Ana and Tom, who missed out last time". */
export function fairLine(ids: readonly string[] | undefined, members: Map<string, Member>): string | null {
	if (!ids?.length) return null;
	return `Fair for ${listNames(names(ids, members), 2)}, who missed out last time`;
}
