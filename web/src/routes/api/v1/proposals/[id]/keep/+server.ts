import { authed, json } from '#lib/server/http';
import { keepPlan } from '#lib/server/proposals';

/** "Keep the plan" after someone can't make it any more: the proposer or an admin clears the review. */
export const POST = authed<{ id: string }>(async ({ params, userId }) => json(await keepPlan(params.id, userId)));
