import { z } from 'zod';
import { handler, json, query } from '#lib/server/http';
import { bankHolidays } from '#lib/server/holidays';
import { dateString } from '#lib/schemas';

const holidaysQuery = z.object({
	country: z.string().length(2).toUpperCase(),
	subdivision: z.string().max(10).optional(),
	from: dateString,
	to: dateString
});

export const GET = handler(async ({ url }) => {
	const q = query(url, holidaysQuery);
	const map = await bankHolidays(q.country, q.subdivision ?? null, { from: q.from, to: q.to });
	return json({ holidays: [...map].map(([date, name]) => ({ date, name })) });
});
