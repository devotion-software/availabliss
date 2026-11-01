export interface PushMessage {
	title: string;
	body: string;
	/** String-only key/values for deep links: kind, groupId, proposalId, eventId, messageId. */
	data: Record<string, string>;
}

export type PushResult = 'sent' | 'invalid' | 'error';
