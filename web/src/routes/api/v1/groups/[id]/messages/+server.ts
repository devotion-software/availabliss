import { z } from 'zod';
import { authed, body, json, query } from '#lib/server/http';
import { listMessages, sendLocation, sendPoll, sendText } from '#lib/server/chat';
import { createMessageSchema } from '#lib/schemas';

const pageQuery = z.object({
	before: z.string().min(1).optional(),
	limit: z.coerce.number().int().min(1).max(100).default(50)
});

export const GET = authed<{ id: string }>(async ({ params, url, userId }) => {
	const { before, limit } = query(url, pageQuery);
	return json(await listMessages(params.id, userId, before ?? null, limit));
});

export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const input = await body(event, createMessageSchema);
	const message =
		'poll' in input
			? await sendPoll(params.id, userId, input.poll)
			: 'location' in input
				? await sendLocation(params.id, userId, input.location, input.replyToId ?? null)
				: await sendText(params.id, userId, input.body, input.replyToId ?? null, 'attachmentIds' in input ? input.attachmentIds : []);
	return json(message, { status: 201 });
});
