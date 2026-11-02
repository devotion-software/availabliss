import { authed, body, json } from '#lib/server/http';
import { createTemplate, listTemplates } from '#lib/server/templates';
import { createTemplateSchema } from '#lib/schemas';

export const GET = authed(async ({ userId }) => json({ templates: await listTemplates(userId) }));

export const POST = authed(async ({ event, userId }) =>
	json(await createTemplate(userId, await body(event, createTemplateSchema)), { status: 201 })
);
