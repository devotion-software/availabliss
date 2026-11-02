import { authed, json } from '#lib/server/http';
import { listLinkedAccounts } from '#lib/server/accounts';

/** Apple / Google sign-ins linked to your account, and which ones this server can link. */
export const GET = authed(async ({ userId }) => json(await listLinkedAccounts(userId)));
