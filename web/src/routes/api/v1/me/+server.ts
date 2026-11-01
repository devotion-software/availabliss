import { authed, body, json, noContent } from '#lib/server/http';
import { deleteAccount, getMe, updateMe } from '#lib/server/users';
import { updateMeSchema } from '#lib/schemas';

export const GET = authed(async ({ userId }) => json(await getMe(userId)));

export const PATCH = authed(async ({ event, userId }) => json(await updateMe(userId, await body(event, updateMeSchema))));

export const DELETE = authed(async ({ userId }) => {
	await deleteAccount(userId);
	return noContent();
});
