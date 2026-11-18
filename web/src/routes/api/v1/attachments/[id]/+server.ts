import { handler } from '#lib/server/http';
import { serveAttachment } from '#lib/server/attachments';

/**
 * A chat attachment's bytes. Needs the signature from its link (`e`, `s`), or a signed-in
 * member of the group. Photos show inline; files download (`?download=1` downloads a photo).
 */
export const GET = handler<{ id: string }>((event) =>
	serveAttachment(event.params.id, 'file', event.url, event.locals.user?.id ?? null, event.request.headers.get('range'))
);
