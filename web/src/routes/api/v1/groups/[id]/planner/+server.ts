import { z } from 'zod';
import { authed, body, json } from '#lib/server/http';
import { planWithAi } from '#lib/server/planner';

const plannerSchema = z.object({ notes: z.string().trim().max(500).nullish() });

/** The optional AI planner (Pro): best time, alternatives, ideas and a checklist. */
export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const { notes } = await body(event, plannerSchema);
	return json(await planWithAi(params.id, userId, notes || null));
});
