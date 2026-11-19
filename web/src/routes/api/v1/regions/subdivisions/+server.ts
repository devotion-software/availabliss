import { handler, json, query } from '#lib/server/http';
import { subdivisions } from '#lib/server/holidays';
import { countryQuerySchema } from '#lib/schemas';

export const GET = handler(async ({ url }) => {
	const { country } = query(url, countryQuerySchema);
	// Same for everyone and rarely changes, so browsers can keep it (the sign-up wizard asks more than once).
	return json({ subdivisions: await subdivisions(country) }, { headers: { 'cache-control': 'public, max-age=3600' } });
});
