import { z } from 'zod';
import { handler, json, query } from '#lib/server/http';
import { bankHolidays } from '#lib/server/holidays';
import { dateString } from '#lib/schemas';

const MAX_DAYS = 400;
const days = (from: string, to: string) => (Date.parse(to) - Date.parse(from)) / 86_400_000;

const holidaysQuery = z
	.object({
		country: z.string().length(2).toUpperCase(),
		subdivision: z.string().max(10).optional(),
		from: dateString,
		to: dateString
	})
	.refine((q) => q.from <= q.to, { message: 'Range ends before it starts' })
	.refine((q) => days(q.from, q.to) <= MAX_DAYS, { message: `Ask for at most ${MAX_DAYS} days at a time` });

export const GET = handler(async ({ url }) => {
	const q = query(url, holidaysQuery);
	const map = await bankHolidays(q.country, q.subdivision ?? null, { from: q.from, to: q.to });
	return json({ holidays: [...map].map(([date, name]) => ({ date, name })) });
});
