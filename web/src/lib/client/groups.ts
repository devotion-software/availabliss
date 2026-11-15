import { api, type Fetch } from './api';
import type { Group, GroupInvite, GroupPreference, GroupSummary, Member, Requirement, TileColor, Weekday } from '#lib/types';

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
}

export const listGroups = (fetch?: Fetch) => api.get<{ groups: GroupSummary[] }>('/groups', { fetch }).then((r) => r.groups);
export const getGroup = (id: string, fetch?: Fetch) => api.get<Group>(`/groups/${id}`, { fetch });
export const createGroup = (body: GroupInput) => api.post<Group>('/groups', body);
export const updateGroup = (id: string, patch: Partial<GroupInput>) => api.patch<Group>(`/groups/${id}`, patch);
export const deleteGroup = (id: string) => api.del(`/groups/${id}`);

export const setMemberRole = (groupId: string, userId: string, role: 'admin' | 'member') =>
	api.patch<Member>(`/groups/${groupId}/members/${userId}`, { role });
/** Removes someone, or leaves the group when `userId` is you. */
export const removeMember = (groupId: string, userId: string) => api.del(`/groups/${groupId}/members/${userId}`);

export const nudgeGroup = (groupId: string) => api.post<{ nudged: number }>(`/groups/${groupId}/nudge`);

/** No emails: a link anyone can use. Emails: one invite each, emailed, only for that person. */
export const createInvite = (groupId: string, emails: string[] = []) =>
	api.post<Invite>(`/groups/${groupId}/invites`, emails.length ? { emails } : {});
export const listInvites = (groupId: string, fetch?: Fetch) =>
	api.get<{ invites: GroupInvite[] }>(`/groups/${groupId}/invites`, { fetch }).then((r) => r.invites);
export const revokeInvite = (groupId: string, inviteId: string) => api.del(`/groups/${groupId}/invites/${inviteId}`);
export const getInvite = (token: string, fetch?: Fetch) => api.get<InvitePreview>(`/invites/${token}`, { fetch });
export const acceptInvite = (token: string) => api.post<Group>(`/invites/${token}/accept`);

/** Every member's preferred times for the group. */
export const listPreferences = (groupId: string, fetch?: Fetch) =>
	api.get<{ preferences: GroupPreference[] }>(`/groups/${groupId}/preferences`, { fetch }).then((r) => r.preferences);
export const addPreference = (
	groupId: string,
	body: { kind: 'dates'; from: string; to: string } | { kind: 'weekly'; days: Weekday[]; window: { from: string; to: string } | null }
) => api.post<GroupPreference>(`/groups/${groupId}/preferences`, body);
export const removePreference = (groupId: string, id: string) => api.del(`/groups/${groupId}/preferences/${id}`);
