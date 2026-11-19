import { authed, badRequest, json, query } from '#lib/server/http';
import { listGroupMedia, MAX_FILE_BYTES, uploadAttachment } from '#lib/server/attachments';
import { groupMediaQuerySchema } from '#lib/schemas';

/** The chat's media page: its photos and GIFs, or files and passes, newest first. */
export const GET = authed<{ id: string }>(async ({ params, url, userId }) => {
	const { kind, before, limit } = query(url, groupMediaQuerySchema);
	return json(await listGroupMedia(params.id, userId, kind, before ?? null, limit));
});

/**
 * Uploads one photo, GIF or file for the group's chat (multipart/form-data, field `file`).
 * Returns the Attachment; send its id in `attachmentIds` with POST /groups/:id/messages.
 */
export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	// Refuse obviously oversized bodies before reading them (multipart adds a little).
	if (Number(event.request.headers.get('content-length') ?? 0) > MAX_FILE_BYTES + 64 * 1024) throw badRequest('Files can be up to 25 MB');
	let form: FormData;
	try {
		form = await event.request.formData();
	} catch {
		throw badRequest('Expected multipart/form-data with a file');
	}
	const file = form.get('file');
	if (!(file instanceof File)) throw badRequest('Expected multipart/form-data with a file');
	return json(await uploadAttachment(params.id, userId, file), { status: 201 });
});
