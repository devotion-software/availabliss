import { authed, json } from '#lib/server/http';
import { messageInfo } from '#lib/server/chat';

export const GET = authed<{ id: string }>(async ({ params, userId }) => json(await messageInfo(params.id, userId)));
