import { body, handler, json } from '#lib/server/http';
import { redeemCode } from '#lib/server/native-auth';
import { nativeExchangeSchema } from '#lib/schemas';

/**
 * Swaps the one-time code from ../callback for a bearer token (single use, 2
 * minutes). `code_verifier` is the secret whose SHA-256 the app sent to ../start.
 */
export const POST = handler(async (event) => {
	const { code, code_verifier } = await body(event, nativeExchangeSchema);
	return json({ token: await redeemCode(code, code_verifier) });
});
