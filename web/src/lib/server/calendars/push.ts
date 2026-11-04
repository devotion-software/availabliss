/**
 * Change notifications ("push") from Google and Microsoft, so a calendar edit
 * reaches groups in seconds instead of at the next poll. Each busy calendar gets
 * a channel (Google) or subscription (Microsoft) that is renewed before it lapses;
 * connections whose busy calendars are all watched are polled rarely, as a backstop.
 * CalDAV has no push for third parties, so those calendars stay on polling.
 */
import { createHmac, randomUUID, timingSafeEqual } from 'node:crypto';
import { eq } from 'drizzle-orm';
import type { CalendarProvider } from '#lib/types';
import { db } from '../db';
import { calendars } from '../db/schema';
import { env } from '../env';
import type { CalendarAdapter, RemoteCalendar } from './types';

/** Channels are replaced once they have less than this left. */
const RENEW_BEFORE_MS = 24 * 60 * 60 * 1000;

/** The URL a provider posts this kind of notification to, or null when push is off. */
export function pushAddress(provider: CalendarProvider): string | null {
	return env.calendarPushUrl ? `${env.calendarPushUrl}/api/v1/calendars/push/${provider}` : null;
}

function secret(): string {
	if (!env.secretsKey) throw new Error('SECRETS_KEY (or BETTER_AUTH_SECRET) must be set');
	return env.secretsKey;
}

/** What the provider echoes back with each notification, proving it came from a channel we made. */
export const pushToken = (calendarId: string) => createHmac('sha256', secret()).update(`calendar-push:${calendarId}`).digest('base64url');

export function verifyPushToken(calendarId: string, token: string | null | undefined): boolean {
	if (!token) return false;
	const a = Buffer.from(token);
	const b = Buffer.from(pushToken(calendarId));
	return a.length === b.length && timingSafeEqual(a, b);
}

type Channel = { id: string; resourceId: string | null };

/**
 * Makes sure each busy calendar of a connection has a live channel, and that
 * calendars no longer busy don't. A channel near expiry is replaced (a new one
 * first, then the old one stopped). Returns true when every busy calendar is
 * watched, so the connection can be polled rarely. Failures only mean polling.
 */
export async function maintainPush<Creds>(
	adapter: CalendarAdapter<Creds>,
	creds: Creds,
	provider: CalendarProvider,
	connectionId: string,
	remote: RemoteCalendar[],
	now = Date.now()
): Promise<boolean> {
	const address = pushAddress(provider);
	if (!address || !adapter.watch) return false;
	const rows = await db.select().from(calendars).where(eq(calendars.connectionId, connectionId));
	const byExternal = new Map(remote.map((c) => [c.externalId, c]));
	let allWatched = rows.some((r) => r.busy);

	for (const row of rows) {
		const live = row.pushId && row.pushExpiresAt && row.pushExpiresAt.getTime() - now > RENEW_BEFORE_MS;
		if (row.busy && live) continue;
		if (!row.busy && !row.pushId) continue;

		const cal = byExternal.get(row.externalId);
		let channel = null;
		if (row.busy && cal) {
			channel = await adapter.watch(creds, cal, { id: randomUUID(), address, token: pushToken(row.id) }).catch((err) => {
				console.warn('[calendars] watch failed', provider, row.id, err);
				return null;
			});
		}
		if (row.busy && !channel) allWatched = false;
		// A failed renewal keeps the old channel until it lapses.
		if (row.busy && !channel && row.pushId) continue;

		await db
			.update(calendars)
			.set({ pushId: channel?.id ?? null, pushResourceId: channel?.resourceId ?? null, pushExpiresAt: channel?.expiresAt ?? null })
			.where(eq(calendars.id, row.id));
		if (row.pushId) await stopChannel(adapter, creds, { id: row.pushId, resourceId: row.pushResourceId });
	}
	return allWatched;
}

/** Stops the channels of these calendars (before the connection is removed). */
export async function stopPush<Creds>(adapter: CalendarAdapter<Creds>, creds: Creds, rows: { pushId: string | null; pushResourceId: string | null }[]) {
	for (const row of rows) if (row.pushId) await stopChannel(adapter, creds, { id: row.pushId, resourceId: row.pushResourceId });
}

async function stopChannel<Creds>(adapter: CalendarAdapter<Creds>, creds: Creds, channel: Channel) {
	// Best effort: a channel we can't stop just lapses, and its notifications are ignored.
	await adapter.unwatch?.(creds, channel).catch((err) => console.warn('[calendars] unwatch failed', channel.id, err));
}
