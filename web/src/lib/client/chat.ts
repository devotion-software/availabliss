import { api, type Fetch } from './api';
import type { Message, MessageInfo, Poll } from '#lib/types';

export interface MessagePage {
	messages: Message[];
	hasMore: boolean;
}

export interface PollInput {
	question: string;
	options: string[];
	multi?: boolean;
}

export const listMessages = (groupId: string, opts: { before?: string; limit?: number } = {}, fetch?: Fetch) =>
	api.get<MessagePage>(`/groups/${groupId}/messages`, { fetch, query: { before: opts.before, limit: opts.limit ?? 50 } });
export const sendMessage = (groupId: string, body: string) => api.post<Message>(`/groups/${groupId}/messages`, { body });
export const sendPoll = (groupId: string, poll: PollInput) => api.post<Message>(`/groups/${groupId}/messages`, { poll });
export const sendReceipt = (groupId: string, upTo: string, state: 'delivered' | 'seen') =>
	api.post(`/groups/${groupId}/messages/receipts`, { upTo, state });
export const getMessageInfo = (messageId: string) => api.get<MessageInfo>(`/messages/${messageId}/info`);
export const votePoll = (pollId: string, optionIds: string[]) => api.post<Poll>(`/polls/${pollId}/vote`, { optionIds });
export const closePoll = (pollId: string) => api.post<Poll>(`/polls/${pollId}/close`);
