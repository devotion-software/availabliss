import { eq, inArray } from 'drizzle-orm';
import { db } from '../db';
import { devices } from '../db/schema';
import { apnsConfigured, sendApns } from './apns';
import { fcmConfigured, sendFcm } from './fcm';
import type { PushMessage } from './types';

export type { PushMessage };

/** Sends to every registered device of the given users and prunes dead tokens. */
export async function sendPush(userIds: string[], message: PushMessage): Promise<void> {
	if (!userIds.length) return;
	const targets = await db.select().from(devices).where(inArray(devices.userId, userIds));
	await Promise.all(
		targets.map(async (device) => {
			try {
				const send = device.platform === 'ios' ? (apnsConfigured() ? sendApns : null) : fcmConfigured() ? sendFcm : null;
				if (!send) return;
				if ((await send(device.token, message)) === 'invalid') await db.delete(devices).where(eq(devices.id, device.id));
			} catch (err) {
				console.warn('[push] send failed', device.platform, err);
			}
		})
	);
}
