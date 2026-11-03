/** Firebase Cloud Messaging HTTP v1 with a service-account OAuth token. */
import { env } from '../env';
import { cachedToken, signJwt } from './jwt';
import type { PushMessage, PushResult } from './types';

interface ServiceAccount {
	project_id: string;
	client_email: string;
	private_key: string;
}

let account: ServiceAccount | null | undefined;
function serviceAccount(): ServiceAccount | null {
	if (account === undefined) {
		account = env.fcmServiceAccount
			? (JSON.parse(Buffer.from(env.fcmServiceAccount, 'base64').toString('utf8')) as ServiceAccount)
			: null;
	}
	return account;
}

export const fcmConfigured = () => serviceAccount() !== null;

const accessToken = cachedToken(50 * 60 * 1000, async () => {
	const sa = serviceAccount()!;
	const now = Math.floor(Date.now() / 1000);
	const assertion = signJwt(
		'RS256',
		{},
		{
			iss: sa.client_email,
			scope: 'https://www.googleapis.com/auth/firebase.messaging',
			aud: 'https://oauth2.googleapis.com/token',
			iat: now,
			exp: now + 3600
		},
		sa.private_key
	);
	const res = await fetch('https://oauth2.googleapis.com/token', {
		method: 'POST',
		headers: { 'content-type': 'application/x-www-form-urlencoded' },
		body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion })
	});
	if (!res.ok) throw new Error(`FCM token exchange failed (${res.status})`);
	return ((await res.json()) as { access_token: string }).access_token;
});

/**
 * In the background Android shows the notification itself: a group's chat messages
 * share a tag so each replaces the last instead of stacking (as the app does in the
 * foreground, see PushNotifier).
 */
export const androidTag = (message: PushMessage) =>
	message.data.kind === 'message' && message.data.groupId ? `message:${message.data.groupId}` : undefined;

export async function sendFcm(token: string, message: PushMessage): Promise<PushResult> {
	const sa = serviceAccount()!;
	const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
		method: 'POST',
		headers: { authorization: `Bearer ${await accessToken()}`, 'content-type': 'application/json' },
		body: JSON.stringify({
			message: {
				token,
				notification: { title: message.title, body: message.body },
				data: message.data,
				android: { priority: 'high', notification: { channel_id: message.data.kind ?? 'general', tag: androidTag(message) } }
			}
		})
	});
	if (res.ok) return 'sent';
	const text = await res.text();
	return res.status === 404 || text.includes('UNREGISTERED') ? 'invalid' : 'error';
}
