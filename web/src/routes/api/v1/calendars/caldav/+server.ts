import { ApiError, authed, body, json } from '#lib/server/http';
import { connectCaldav } from '#lib/server/calendars';
import { ReauthRequired } from '#lib/server/calendars/types';
import { caldavSchema } from '#lib/schemas';

export const POST = authed(async ({ event, userId }) => {
	const input = await body(event, caldavSchema);
	try {
		return json(await connectCaldav(userId, input), { status: 201 });
	} catch (err) {
		if (err instanceof ReauthRequired) throw new ApiError('validation_error', 'That username or app-specific password was rejected');
		throw err;
	}
});
