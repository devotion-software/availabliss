import type { RequestHandler } from './$types';
import { onMicrosoftNotifications } from '#lib/server/calendars/webhooks';

/**
 * Microsoft Graph change notifications. Creating a subscription first sends a
 * `validationToken`, which must be echoed back as plain text; real notifications
 * are acknowledged with 202 (Graph expects an answer within a few seconds).
 */
export const POST: RequestHandler = async ({ request, url }) => {
	const validationToken = url.searchParams.get('validationToken');
	if (validationToken !== null) {
		return new Response(validationToken, { status: 200, headers: { 'content-type': 'text/plain' } });
	}
	await onMicrosoftNotifications(await request.json().catch(() => null));
	return new Response(null, { status: 202 });
};
