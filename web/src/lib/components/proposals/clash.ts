/** Wording for proposals that clash with someone's busy time. */
import type { Member } from '#lib/types';
import { firstName, listNames } from '#lib/client/format';

/**
 * "Priya is busy then", "You and Priya are busy then",
 * "Priya, Alex, Jo and 2 others are busy then". Null when nobody is.
 */
export function clashText(busyIds: readonly string[], members: Map<string, Member>, meId: string): string | null {
	if (!busyIds.length) return null;
	const mine = busyIds.includes(meId);
	const others = busyIds.filter((id) => id !== meId).map((id) => firstName(members.get(id)?.name ?? 'Someone'));
	const names = mine ? ['You', ...others] : others;
	if (names.length === 1 && mine) return "You're busy then";
	const plural = names.length > 1;
	return `${listNames(names, 3)} ${plural ? 'are' : 'is'} busy then`;
}

/** Short form for compact rows: "Priya busy" / "3 busy". */
export function clashShort(busyIds: readonly string[], members: Map<string, Member>): string | null {
	if (!busyIds.length) return null;
	if (busyIds.length > 1) return `${busyIds.length} busy then`;
	return `${firstName(members.get(busyIds[0])?.name ?? 'Someone')} busy then`;
}
