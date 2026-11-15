import { error } from '@sveltejs/kit';
import { localFile } from '#lib/server/media';
import type { RequestHandler } from './$types';

/** Development only: profile pictures kept on disk when R2 isn't configured. */
export const GET: RequestHandler = async ({ params }) => {
	const bytes = await localFile(params.key);
	if (!bytes) error(404, 'Not found');
	return new Response(new Uint8Array(bytes), { headers: { 'content-type': 'image/jpeg', 'cache-control': 'public, max-age=31536000, immutable' } });
};
