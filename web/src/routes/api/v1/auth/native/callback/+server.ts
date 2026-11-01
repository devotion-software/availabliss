import { handler, redirectTo } from '#lib/server/http';
import { issueCode, NATIVE_CALLBACK } from '#lib/server/native-auth';

/** End of the native web-flow sign-in: send the app a one-time code. */
export const GET = handler(({ locals, url }) => {
	const token = locals.session?.token;
	if (!token) redirectTo(`${NATIVE_CALLBACK}?error=${encodeURIComponent(url.searchParams.get('error') ?? 'sign_in_failed')}`);
	redirectTo(`${NATIVE_CALLBACK}?code=${issueCode(token)}`);
});
