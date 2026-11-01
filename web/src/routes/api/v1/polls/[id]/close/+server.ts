import { authed, json } from '#lib/server/http';
import { closePoll } from '#lib/server/chat';

export const POST = authed<{ id: string }>(async ({ params, userId }) => json(await closePoll(params.id, userId)));
