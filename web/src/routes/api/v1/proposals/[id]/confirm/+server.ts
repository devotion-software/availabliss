import { authed, json } from '#lib/server/http';
import { confirmAnyway } from '#lib/server/proposals';

/** "Confirm anyway": the proposer or an admin confirms before everyone has accepted. */
export const POST = authed<{ id: string }>(async ({ params, userId }) => json(await confirmAnyway(params.id, userId)));
