import { z } from 'zod';
import { handler, json, query } from '#lib/server/http';
import { subdivisions } from '#lib/server/holidays';

export const GET = handler(async ({ url }) => {
	const { country } = query(url, z.object({ country: z.string().length(2).toUpperCase() }));
	return json({ subdivisions: await subdivisions(country) });
});
