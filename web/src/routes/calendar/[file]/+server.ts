import { error } from '@sveltejs/kit';
import { renderCalendarFeed } from '#lib/server/calendar-feeds';
import type { RequestHandler } from './$types';

/**
 * A subscribable calendar: /calendar/<token>.ics, optionally ?group=<id>. No session: the secret
 * token is the key (calendar apps can't sign in). Polled by calendar apps, so it's outside /api/v1's
 * rate limits.
 */
export const GET: RequestHandler = async ({ params, url }) => {
	const token = params.file.replace(/\.ics$/, '');
	if (!/^[A-Za-z0-9]{20,64}$/.test(token)) error(404, 'Not found');
	try {
		const body = await renderCalendarFeed(token, url.searchParams.get('group'), url.hostname);
		return new Response(body, {
			headers: {
				'content-type': 'text/calendar; charset=utf-8',
				'cache-control': 'private, max-age=900',
				'content-disposition': 'inline; filename="availabliss.ics"'
			}
		});
	} catch (err) {
		if ((err as { status?: number }).status === 404 || (err as { code?: string }).code === 'not_found') error(404, 'Not found');
		throw err;
	}
};
