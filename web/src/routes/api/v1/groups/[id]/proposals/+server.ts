import { authed, body, json, query } from '#lib/server/http';
import { createProposal, listProposals } from '#lib/server/proposals';
import { createProposalSchema, proposalsQuerySchema } from '#lib/schemas';

export const GET = authed<{ id: string }>(async ({ params, url, userId }) => {
	const { status } = query(url, proposalsQuerySchema);
	return json({ proposals: await listProposals(params.id, userId, status) });
});

export const POST = authed<{ id: string }>(async ({ event, params, userId }) =>
	json(await createProposal(params.id, userId, await body(event, createProposalSchema)), { status: 201 })
);
