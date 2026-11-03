import { z } from 'zod';
import { ApiError, authed, badRequest, body, json, notFound } from '#lib/server/http';
import { isOAuthProvider, takePendingLink } from '#lib/server/calendars/oauth';
import { connectOAuth, getConnection } from '#lib/server/calendars';

/**
 * For native apps: finishes a Google / Microsoft connection after the callback
 * redirected to `availabliss://calendars?complete=<code>`. Only the user who
 * started the flow can complete it.
 */
export const POST = authed<{ provider: string }>(async ({ event, params, userId }) => {
	if (!isOAuthProvider(params.provider)) throw notFound('Unknown calendar provider');
	const { code } = await body(event, z.object({ code: z.string().min(1).max(100) }));
	const providerCode = await takePendingLink(code, userId, params.provider);
	let id: string;
	try {
		id = await connectOAuth(userId, params.provider, providerCode);
	} catch (err) {
		if (err instanceof ApiError) throw err;
		console.warn('[calendars] oauth complete failed', err);
		throw badRequest("Couldn't connect that calendar, please try again");
	}
	return json(await getConnection(userId, id));
});
