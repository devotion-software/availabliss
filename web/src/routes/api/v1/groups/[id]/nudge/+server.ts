import { authed, json } from '#lib/server/http';
import { nudgeUnsynced } from '#lib/server/nudge';

export const POST = authed<{ id: string }>(async ({ params, userId }) => json(await nudgeUnsynced(params.id, userId)));
