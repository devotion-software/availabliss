import { handler, json } from '#lib/server/http';
import { detectRegion } from '#lib/server/holidays';

export const GET = handler(({ request }) => json(detectRegion(request.headers)));
