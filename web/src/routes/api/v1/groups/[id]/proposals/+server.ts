import { z } from 'zod';
import { authed, body, json, query } from '#lib/server/http';
import { createProposal, listProposals } from '#lib/server/proposals';
import { createProposalSchema } from '#lib/schemas';

const statusQuery = z.object({ status: z.enum(['open', 'confirmed', 'cancelled']).optional() });

export const GET = authed<{ id: string }>(async ({ params, url, userId }) => {
	const { status } = query(url, statusQuery);
	return json({ proposals: await listProposals(params.id, userId, status) });
});

export const POST = authed<{ id: string }>(async ({ event, params, userId }) =>
	json(await createProposal(params.id, userId, await body(event, createProposalSchema)), { status: 201 })
);
