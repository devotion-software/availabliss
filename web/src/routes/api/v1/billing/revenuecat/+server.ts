import { timingSafeEqual } from 'node:crypto';
import { handler, json } from '#lib/server/http';
import { handleRevenueCatEvent, type RcEvent } from '#lib/server/billing';
import { env } from '#lib/server/env';

function authorised(header: string | null): boolean {
	const expected = env.revenuecat.webhookAuth;
	if (!expected || !header) return false;
	const a = Buffer.from(header);
	const b = Buffer.from(expected);
	return a.length === b.length && timingSafeEqual(a, b);
}

/**
 * RevenueCat webhook (Project settings → Integrations → Webhooks). Set the
 * Authorization header value to REVENUECAT_WEBHOOK_AUTH.
 */
export const POST = handler(async ({ request }) => {
	if (!authorised(request.headers.get('authorization'))) return json({ message: 'Unauthorized', code: 'unauthorized' }, { status: 401 });
	const body = (await request.json().catch(() => null)) as { event?: RcEvent } | null;
	if (!body?.event?.type) return json({ message: 'Expected a RevenueCat event', code: 'validation_error' }, { status: 400 });
	await handleRevenueCatEvent(body.event);
	return json({ ok: true });
});
