import { authed, body, json } from '#lib/server/http';
import { planWithAi } from '#lib/server/planner';
import { plannerSchema } from '#lib/schemas';

/** The optional AI planner (Pro): best time, alternatives, ideas and a checklist. */
export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const { notes } = await body(event, plannerSchema);
	return json(await planWithAi(params.id, userId, notes || null));
});
