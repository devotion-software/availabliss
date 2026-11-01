import { authed, body, json } from '#lib/server/http';
import { createBlock, listBlocks } from '#lib/server/blocks';
import { publishAvailabilityChanged } from '#lib/server/realtime';
import { createBlockSchema } from '#lib/schemas';

export const GET = authed(async ({ userId }) => json({ blocks: await listBlocks(userId) }));

export const POST = authed(async ({ event, userId }) => {
	const block = await createBlock(userId, await body(event, createBlockSchema));
	await publishAvailabilityChanged(userId);
	return json(block, { status: 201 });
});
