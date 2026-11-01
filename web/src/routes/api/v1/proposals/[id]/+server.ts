import { authed, json, noContent } from '#lib/server/http';
import { cancelProposal, getProposal } from '#lib/server/proposals';

export const GET = authed<{ id: string }>(async ({ params, userId }) => json(await getProposal(params.id, userId)));

export const DELETE = authed<{ id: string }>(async ({ params, userId }) => {
	await cancelProposal(params.id, userId);
	return noContent();
});
