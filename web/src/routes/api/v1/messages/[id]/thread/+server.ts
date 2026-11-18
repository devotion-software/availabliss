import { authed, json } from '#lib/server/http';
import { messageThread } from '#lib/server/chat';

export const GET = authed<{ id: string }>(async ({ params, userId }) => json(await messageThread(params.id, userId)));
