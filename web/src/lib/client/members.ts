/** Helpers for showing group members. */
import type { Group, Member, Role } from '#lib/types';
import { firstName } from './format';

export const isAdminRole = (role: Role | undefined) => role === 'owner' || role === 'admin';

export function memberMap(group: Pick<Group, 'members'>): Map<string, Member> {
	return new Map(group.members.map((m) => [m.userId, m]));
}

/** "Sam (you)", "Priya", or a fallback for people who've left. */
export function memberLabel(members: Map<string, Member>, userId: string | null, meId: string, opts: { first?: boolean } = {}): string {
	if (!userId) return 'Availabliss';
	const m = members.get(userId);
	const name = m ? (opts.first ? firstName(m.name) : m.name) : 'Someone';
	return userId === meId ? `${name} (you)` : name;
}

export const ROLE_LABEL: Record<Role, string> = { owner: 'Owner', admin: 'Admin', member: 'Member' };

/** "6 members · you're an admin" */
export function groupSubtitle(group: Group): string {
	const count = `${group.members.length} member${group.members.length === 1 ? '' : 's'}`;
	if (group.role === 'owner') return `${count} · you're the owner`;
	if (group.role === 'admin') return `${count} · you're an admin`;
	return count;
}
