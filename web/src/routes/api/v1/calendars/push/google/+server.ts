import type { RequestHandler } from './$types';
import { onGoogleNotification } from '#lib/server/calendars/webhooks';

/**
 * Google Calendar change notifications (events.watch channels). Answered 200 even
 * when ignored: an error would only make Google retry.
 */
export const POST: RequestHandler = async ({ request }) => {
	await onGoogleNotification(request.headers);
	return new Response(null, { status: 200 });
};
