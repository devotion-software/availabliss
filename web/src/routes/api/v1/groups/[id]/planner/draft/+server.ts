import { authed, body, json } from '#lib/server/http';
import { draftProposal } from '#lib/server/planner';
import { plannerDraftSchema } from '#lib/schemas';

/** Pro: an AI-drafted title and message for a proposal at the time you picked. */
export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const { start, end, notes } = await body(event, plannerDraftSchema);
	return json(await draftProposal(params.id, userId, { start, end, notes: notes || null }));
});
