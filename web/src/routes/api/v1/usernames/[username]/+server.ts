import { authed, json } from '#lib/server/http';
import { checkUsername } from '#lib/server/usernames';

/** Whether a username is valid and free (for choosing yours), and who has it (for inviting them). */
export const GET = authed<{ username: string }>(async ({ params, userId }) => json(await checkUsername(params.username, userId)));
