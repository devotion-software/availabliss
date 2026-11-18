import { handler, json, query } from '#lib/server/http';
import { bankHolidays } from '#lib/server/holidays';
import { holidaysQuerySchema } from '#lib/schemas';

export const GET = handler(async ({ url }) => {
	const q = query(url, holidaysQuerySchema);
	const map = await bankHolidays(q.country, q.subdivision ?? null, { from: q.from, to: q.to });
	return json({ holidays: [...map].map(([date, name]) => ({ date, name })) });
});
