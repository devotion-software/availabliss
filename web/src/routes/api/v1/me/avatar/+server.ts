import { eq } from 'drizzle-orm';
import { authed, json } from '#lib/server/http';
import { db } from '#lib/server/db';
import { user } from '#lib/server/db/schema';
import { getMe } from '#lib/server/users';
import { deleteStored, processAvatar, putAvatar } from '#lib/server/media';
import { keyOf } from '#lib/server/media-urls';

/** Only ever deletes a picture stored as this user's own avatar. */
const deleteOwnAvatar = async (userId: string, url: string | null) => {
	if (keyOf(url)?.startsWith(`avatars/${userId}/`)) await deleteStored(url);
};

/**
 * Sets your profile picture. Body: the image bytes (any common format), sent as-is with its
 * image/* content type. Returns the updated User.
 */
export const PUT = authed(async ({ event, userId }) => {
	const bytes = await processAvatar(await event.request.arrayBuffer());
	const before = await getMe(userId);
	const url = await putAvatar(userId, bytes);
	await db.update(user).set({ image: url }).where(eq(user.id, userId));
	await deleteOwnAvatar(userId, before.image);
	return json(await getMe(userId));
});

/** Removes your profile picture (back to initials). */
export const DELETE = authed(async ({ userId }) => {
	const before = await getMe(userId);
	await db.update(user).set({ image: null }).where(eq(user.id, userId));
	await deleteOwnAvatar(userId, before.image);
	return json(await getMe(userId));
});
