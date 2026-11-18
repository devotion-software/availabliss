import { handler } from '#lib/server/http';
import { serveAttachment } from '#lib/server/attachments';

/** A photo attachment's smaller preview, signed like the full image. */
export const GET = handler<{ id: string }>((event) =>
	serveAttachment(event.params.id, 'thumb', event.url, event.locals.user?.id ?? null, null)
);
