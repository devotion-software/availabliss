import { handler, redirectTo } from '#lib/server/http';
import { decodeFlow, FLOW_COOKIE, FLOW_COOKIE_PATH, issueCode, NATIVE_CALLBACK, sessionFromFlow } from '#lib/server/native-auth';

const fail = (reason: string): never => redirectTo(`${NATIVE_CALLBACK}?error=${encodeURIComponent(reason)}`);

/**
 * End of the native web-flow sign-in: send the app a one-time code. Only for a
 * browser that went through ../:provider/start (it holds the flow cookie) and
 * only for the session that sign-in just created.
 */
export const GET = handler(async ({ locals, url, cookies }) => {
	const flow = decodeFlow(cookies.get(FLOW_COOKIE));
	cookies.delete(FLOW_COOKIE, { path: FLOW_COOKIE_PATH });
	const session = locals.session;
	if (!session?.token) fail(url.searchParams.get('error') ?? 'sign_in_failed');
	if (!flow || !sessionFromFlow(flow, new Date(session!.createdAt))) fail('sign_in_not_started_here');
	redirectTo(`${NATIVE_CALLBACK}?code=${await issueCode(session!.token, flow!.challenge)}`);
});
