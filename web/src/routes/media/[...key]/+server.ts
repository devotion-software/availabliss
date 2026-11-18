import { error } from '@sveltejs/kit';
import { getPicture } from '#lib/server/media';
import { verifyMediaSignature } from '#lib/server/media-urls';
import type { RequestHandler } from './$types';

/**
 * Profile pictures, group photos and event covers, only with a valid signed link (see
 * media-urls.ts): the files themselves are private. Links stay the same all day, so they're
 * cached by the browser (not by shared caches, as they're private).
 */
export const GET: RequestHandler = async ({ params, url }) => {
	if (!verifyMediaSignature(params.key, url.searchParams.get('e'), url.searchParams.get('s'))) error(404, 'Not found');
	const picture = await getPicture(params.key);
	if (!picture) error(404, 'Not found');
	const headers = new Headers(picture.headers);
	headers.set('content-type', 'image/jpeg');
	headers.set('cache-control', 'private, max-age=604800, immutable');
	headers.set('x-content-type-options', 'nosniff');
	return new Response(picture.body as BodyInit, { headers });
};
