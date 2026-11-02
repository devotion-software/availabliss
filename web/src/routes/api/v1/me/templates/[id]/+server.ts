import { authed, body, json, noContent } from '#lib/server/http';
import { deleteTemplate, updateTemplate } from '#lib/server/templates';
import { updateTemplateSchema } from '#lib/schemas';

export const PATCH = authed<{ id: string }>(async ({ event, params, userId }) =>
	json(await updateTemplate(userId, params.id, await body(event, updateTemplateSchema)))
);

export const DELETE = authed<{ id: string }>(async ({ params, userId }) => {
	await deleteTemplate(userId, params.id);
	return noContent();
});
