import { authed, json } from '#lib/server/http';
import { listChats } from '#lib/server/chats';

/** Every group chat you're in, latest message first (Activity's Chats tab). */
export const GET = authed(async ({ userId }) => json({ chats: await listChats(userId) }));
