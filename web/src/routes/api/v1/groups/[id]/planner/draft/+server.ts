import { z } from 'zod';
import { authed, body, json } from '#lib/server/http';
import { draftProposal } from '#lib/server/planner';

const draftSchema = z
	.object({ start: z.iso.datetime(), end: z.iso.datetime(), notes: z.string().trim().max(500).nullish() })
	.refine((d) => d.end > d.start, 'The end must be after the start');

/** Pro: an AI-drafted title and message for a proposal at the time you picked. */
export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const { start, end, notes } = await body(event, draftSchema);
	return json(await draftProposal(params.id, userId, { start, end, notes: notes || null }));
});
