import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { error } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

/** docs/openapi.json for the dev-only API reference (read fresh, so `bun run openapi` shows up on reload). */
export const GET: RequestHandler = async () => {
	if (!import.meta.env.DEV) error(404, 'Not found');
	const spec = await readFile(resolve(process.cwd(), '../docs/openapi.json'), 'utf8');
	return new Response(spec, { headers: { 'content-type': 'application/json', 'cache-control': 'no-store' } });
};
