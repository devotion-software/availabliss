import { authed, body, json, noContent } from '#lib/server/http';
import { deleteBlock, updateBlock } from '#lib/server/blocks';
import { publishAvailabilityChanged } from '#lib/server/realtime';
import { updateBlockSchema } from '#lib/schemas';

export const PATCH = authed<{ id: string }>(async ({ event, params, userId }) => {
	const block = await updateBlock(userId, params.id, await body(event, updateBlockSchema));
	await publishAvailabilityChanged(userId);
	return json(block);
});

export const DELETE = authed<{ id: string }>(async ({ params, userId }) => {
	await deleteBlock(userId, params.id);
	await publishAvailabilityChanged(userId);
	return noContent();
});
