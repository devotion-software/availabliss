import { handler, json, query } from '#lib/server/http';
import { schoolAuthorities } from '#lib/server/holidays';
import { countryQuerySchema } from '#lib/schemas';

export const GET = handler(async ({ url }) => {
	const { country } = query(url, countryQuerySchema);
	return json({ authorities: await schoolAuthorities(country) });
});
