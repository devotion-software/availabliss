import { z } from 'zod';
import { authed, body, json, query } from '#lib/server/http';
import { createEvent, listEvents } from '#lib/server/events';
import { createEventSchema, isoDateTime } from '#lib/schemas';

const DAY = 24 * 60 * 60 * 1000;
const rangeQuery = z.object({ from: isoDateTime.optional(), to: isoDateTime.optional() });

export const GET = authed<{ id: string }>(async ({ params, url, userId }) => {
	const q = query(url, rangeQuery);
	const from = q.from ? new Date(q.from) : new Date(Date.now() - 30 * DAY);
	const to = q.to ? new Date(q.to) : new Date(Date.now() + 365 * DAY);
	return json({ events: await listEvents(params.id, userId, from, to) });
});

export const POST = authed<{ id: string }>(async ({ event, params, userId }) =>
	json(await createEvent(params.id, userId, await body(event, createEventSchema)), { status: 201 })
);
