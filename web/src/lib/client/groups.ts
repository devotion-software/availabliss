import { api, type Fetch } from './api';
import type { Group, GroupSummary, Member, Requirement, TileColor } from '#lib/types';

export interface GroupInput {
	name: string;
	color?: TileColor;
	requirement?: Requirement;
}

export interface Invite {
	token: string;
	url: string;
	expiresAt: string;
}

export interface InvitePreview {
	groupName: string;
	inviterName: string;
	memberCount: number;
	expiresAt: string;
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

export const createInvite = (groupId: string, email?: string) =>
	api.post<Invite>(`/groups/${groupId}/invites`, email ? { email } : {});
export const getInvite = (token: string, fetch?: Fetch) => api.get<InvitePreview>(`/invites/${token}`, { fetch });
export const acceptInvite = (token: string) => api.post<Group>(`/invites/${token}/accept`);
