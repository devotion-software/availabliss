import { auth } from '#lib/auth';
import type { RequestHandler } from './$types';

// Mount Better Auth directly as a catch-all route (robust across dev and adapter-node).
export const GET: RequestHandler = ({ request }) => auth.handler(request);
export const POST: RequestHandler = ({ request }) => auth.handler(request);
