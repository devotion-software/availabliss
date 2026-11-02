import { api, type Fetch } from './api';
import type { Proposal, ResponseKind } from '#lib/types';

export interface ProposalInput {
	start: string;
	end: string;
	allDay?: boolean;
	title?: string | null;
	location?: string | null;
	note?: string | null;
}

export const listProposals = (groupId: string, status?: Proposal['status'], fetch?: Fetch) =>
	api.get<{ proposals: Proposal[] }>(`/groups/${groupId}/proposals`, { fetch, query: { status } }).then((r) => r.proposals);
export const getProposal = (id: string, fetch?: Fetch) => api.get<Proposal>(`/proposals/${id}`, { fetch });
export const createProposal = (groupId: string, body: ProposalInput) => api.post<Proposal>(`/groups/${groupId}/proposals`, body);
export const respondToProposal = (id: string, response: 'accepted' | 'declined') =>
	api.post<Proposal>(`/proposals/${id}/respond`, { response });
export const overrideResponse = (id: string, userId: string, response: ResponseKind) =>
	api.post<Proposal>(`/proposals/${id}/override`, { userId, response });
export const cancelProposal = (id: string) => api.del(`/proposals/${id}`);
/** Members who are busy during a time, to warn before proposing it. */
export const getConflicts = (groupId: string, start: string, end: string) =>
	api.get<{ busyMemberIds: string[] }>(`/groups/${groupId}/conflicts`, { query: { start, end } }).then((r) => r.busyMemberIds);
