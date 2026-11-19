import { api, type Fetch } from './api';
import type { Group, GroupInvite, GroupPreference, GroupSummary, Member, Requirement, Rhythm, Slot, TileColor, Weekday } from '#lib/types';

export interface GroupInput {
	name: string;
	color?: TileColor;
	requirement?: Requirement;
}

export interface Invite {
	token: string;
	url: string;
	expiresAt: string;
	/** Every invite the request made (one per email). */
	invites: GroupInvite[];
}

export interface InvitePreview {
	groupName: string;
	inviterName: string;
	memberCount: number;
	expiresAt: string;
	/** Who an emailed invite is for; null for a link anyone can use. */
	email: string | null;
	/** Who it was sent to by username, or null. */
	username: string | null;
	/** Sent to one person's Activity, so they can decline it (older servers omit it). */
	personal?: boolean;
}

export const listGroups = (fetch?: Fetch) => api.get<{ groups: GroupSummary[] }>('/groups', { fetch }).then((r) => r.groups);
export const getGroup = (id: string, fetch?: Fetch) => api.get<Group>(`/groups/${id}`, { fetch });
export const createGroup = (body: GroupInput) => api.post<Group>('/groups', body);
/**
 * `archived` moves the group to (true) or out of (false) the Archived section of everyone's groups list;
 * `autoArchive` has it archive itself once its event has passed; `rhythm` is how often it meets (null for none).
 */
export const updateGroup = (id: string, patch: Partial<GroupInput> & { archived?: boolean; autoArchive?: boolean; rhythm?: Rhythm | null }) =>
	api.patch<Group>(`/groups/${id}`, patch);

/** The next time near the group's rhythm: the date it points at, and the free time nearest it (null if none). */
export interface NextSuggestion {
	target: string;
	slot: Slot | null;
}
/** How often a group meets, as offered in its settings. */
export const RHYTHM_LABELS: Record<Rhythm['every'], string> = {
	week: 'Every week',
	'two-weeks': 'Every two weeks',
	'three-weeks': 'Every three weeks',
	month: 'Every month',
	'two-months': 'Every two months',
	'three-months': 'Every three months',
	'six-months': 'Every six months',
	year: 'Every year'
};
/** Null when the group has no rhythm. */
export const getNextSuggestion = (id: string, fetch?: Fetch) =>
	api.get<{ suggestion: NextSuggestion | null }>(`/groups/${id}/next`, { fetch }).then((r) => r.suggestion);
/**
 * Groups you're leaving or deleting from this tab, so the live "you're no longer in it" event
 * that follows doesn't announce it a second time.
 */
export const leavingGroups = new Set<string>();
export const deleteGroup = (id: string) => {
	leavingGroups.add(id);
	return api.del(`/groups/${id}`).catch((err) => {
		leavingGroups.delete(id);
		throw err;
	});
};
/** Sets the group's photo (admins; the server shrinks it to fit 512 px). */
export const setGroupImage = (id: string, file: Blob) => api.put<Group>(`/groups/${id}/image`, file);
/** Removes the group's photo, back to the monogram tile. */
export const removeGroupImage = (id: string) => api.del<Group>(`/groups/${id}/image`);

export const setMemberRole = (groupId: string, userId: string, role: 'admin' | 'member') =>
	api.patch<Member>(`/groups/${groupId}/members/${userId}`, { role });
/** Sets (or with null, clears) the name you go by in the group. */
export const setMyName = (groupId: string, displayName: string | null) => api.patch<Member>(`/groups/${groupId}/members/me`, { displayName });
/** Sets (or with null, clears) the name someone goes by in the group: your own, or as an admin anyone's. */
export const setMemberName = (groupId: string, userId: string, displayName: string | null) =>
	api.patch<Member>(`/groups/${groupId}/members/${userId}`, { displayName });
/** Removes someone (an admin removing another member). */
export const removeMember = (groupId: string, userId: string) => api.del(`/groups/${groupId}/members/${userId}`);
/** Leaves the group. The owner names the admin who becomes the owner. */
export const leaveGroup = (groupId: string, newOwnerId: string | null) => {
	leavingGroups.add(groupId);
	return api.post(`/groups/${groupId}/leave`, newOwnerId ? { newOwnerId } : {}).catch((err) => {
		leavingGroups.delete(groupId);
		throw err;
	});
};

export const nudgeGroup = (groupId: string) => api.post<{ nudged: number }>(`/groups/${groupId}/nudge`);

/**
 * Nobody: a link anyone can use, by `maxUses` people (null: no limit; left out: one). Emails and
 * usernames: one invite each (emailed, or sent to their Activity), only for that person. User ids:
 * likewise to their Activity, for people already in a group with you.
 */
export const createInvite = (groupId: string, emails: string[] = [], usernames: string[] = [], userIds: string[] = [], maxUses?: number | null) =>
	api.post<Invite>(`/groups/${groupId}/invites`, {
		...(emails.length && { emails }),
		...(usernames.length && { usernames }),
		...(userIds.length && { userIds }),
		...(maxUses !== undefined && { maxUses })
	});

/** The most people a link can be for, short of no limit. */
export const LINK_MAX_USES = 1000;
/** What's typed for how many people can join with a link, as a whole number from 1 to {@link LINK_MAX_USES}. */
export function clampLinkUses(value: number): number {
	const n = Math.round(Number(value));
	return Number.isFinite(n) ? Math.min(Math.max(n, 1), LINK_MAX_USES) : 1;
}

/** How many people can still join with a link (null: no limit). */
const usesLeft = (i: GroupInvite) => (i.maxUses === null ? null : Math.max(i.maxUses - i.uses, 0));

/** "One person can join with this link." / "5 people can…" / "3 of 5 people can still…" / "Anyone with this link can join." */
export function linkSummary(i: GroupInvite): string {
	const left = usesLeft(i);
	if (left === null) return 'Anyone with this link can join.';
	const total = i.maxUses ?? 1;
	if (total === 1) return 'One person can join with this link.';
	return left === total ? `${total} people can join with this link.` : `${left} of ${total} people can still join with this link.`;
}

/** For the invites list: "Link for one person" / "3 of 5 left" / "Unlimited · 4 joined". */
export function linkUsage(i: GroupInvite): string {
	const left = usesLeft(i);
	if (left === null) return i.uses ? `Unlimited · ${i.uses} joined` : 'Unlimited';
	const total = i.maxUses ?? 1;
	return total === 1 ? 'Link for one person' : `${left} of ${total} left`;
}
export const listInvites = (groupId: string, fetch?: Fetch) =>
	api.get<{ invites: GroupInvite[] }>(`/groups/${groupId}/invites`, { fetch }).then((r) => r.invites);
export const revokeInvite = (groupId: string, inviteId: string) => api.del(`/groups/${groupId}/invites/${inviteId}`);
export const getInvite = (token: string, fetch?: Fetch) => api.get<InvitePreview>(`/invites/${token}`, { fetch });
/** `joined`: false when you were already in the group. */
export const acceptInvite = (token: string) => api.post<Group & { joined?: boolean }>(`/invites/${token}/accept`);
/** Turns down an invite sent to you; it and its Activity item go. */
export const declineInvite = (token: string) => api.post(`/invites/${token}/decline`);

/** Every member's preferred times for the group. */
export const listPreferences = (groupId: string, fetch?: Fetch) =>
	api.get<{ preferences: GroupPreference[] }>(`/groups/${groupId}/preferences`, { fetch }).then((r) => r.preferences);
export const addPreference = (
	groupId: string,
	body: { kind: 'dates'; from: string; to: string } | { kind: 'weekly'; days: Weekday[]; window: { from: string; to: string } | null }
) => api.post<GroupPreference>(`/groups/${groupId}/preferences`, body);
/** Changes one of your preferred times in place (same body as adding one). */
export const updatePreference = (groupId: string, id: string, body: Parameters<typeof addPreference>[1]) =>
	api.put<GroupPreference>(`/groups/${groupId}/preferences/${id}`, body);
export const removePreference = (groupId: string, id: string) => api.del(`/groups/${groupId}/preferences/${id}`);
