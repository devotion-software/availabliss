import { handler, json } from '#lib/server/http';
import { countries } from '#lib/server/holidays';

export const GET = handler(async () => json({ countries: await countries() }, { headers: { 'cache-control': 'public, max-age=86400' } }));
