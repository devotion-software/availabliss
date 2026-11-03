import { z } from 'zod';
import { body, handler, json } from '#lib/server/http';
import { redeemCode } from '#lib/server/native-auth';

/**
 * Swaps the one-time code from ../callback for a bearer token (single use, 2
 * minutes). `code_verifier` is the secret whose SHA-256 the app sent to ../start.
 */
export const POST = handler(async (event) => {
	const { code, code_verifier } = await body(
		event,
		z.object({ code: z.string().min(16).max(64), code_verifier: z.string().regex(/^[A-Za-z0-9._~-]{43,128}$/) })
	);
	return json({ token: await redeemCode(code, code_verifier) });
});
