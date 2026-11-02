import { z } from 'zod';
import { body, handler, json } from '#lib/server/http';
import { redeemCode } from '#lib/server/native-auth';

/** Swaps the one-time code from ../callback for a bearer token (single use, 2 minutes). */
export const POST = handler(async (event) => {
	const { code } = await body(event, z.object({ code: z.string().min(16).max(64) }));
	return json({ token: await redeemCode(code) });
});
