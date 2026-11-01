/** Apple Push Notification service over HTTP/2 with token-based auth. */
import { connect, type ClientHttp2Session } from 'node:http2';
import { env } from '../env';
import { cachedToken, signJwt } from './jwt';
import type { PushMessage, PushResult } from './types';

export const apnsConfigured = () => Boolean(env.apns.keyId && env.apns.key && env.apple.teamId && env.apple.bundleId);

// Apple asks for provider tokens to be refreshed between 20 and 60 minutes.
const providerToken = cachedToken(45 * 60 * 1000, () =>
	signJwt('ES256', { kid: env.apns.keyId! }, { iss: env.apple.teamId!, iat: Math.floor(Date.now() / 1000) }, env.apns.key!)
);

let session: ClientHttp2Session | null = null;
function client(): ClientHttp2Session {
	if (session && !session.closed && !session.destroyed) return session;
	session = connect(env.apns.production ? 'https://api.push.apple.com' : 'https://api.sandbox.push.apple.com');
	session.on('error', () => (session = null));
	session.on('goaway', () => (session = null));
	return session;
}

export async function sendApns(token: string, message: PushMessage): Promise<PushResult> {
	const payload = JSON.stringify({
		aps: { alert: { title: message.title, body: message.body }, sound: 'default', 'thread-id': message.data.groupId ?? undefined },
		...message.data
	});
	const authorization = `bearer ${await providerToken()}`;

	return new Promise((resolve) => {
		const req = client().request({
			':method': 'POST',
			':path': `/3/device/${token}`,
			authorization,
			'apns-topic': env.apple.bundleId!,
			'apns-push-type': 'alert',
			'content-type': 'application/json'
		});
		let status = 0;
		let body = '';
		req.on('response', (headers) => (status = Number(headers[':status'])));
		req.on('data', (chunk) => (body += chunk));
		req.on('error', () => resolve('error'));
		req.on('end', () => {
			if (status === 200) return resolve('sent');
			// 410 = token no longer active; BadDeviceToken = wrong environment or garbage.
			resolve(status === 410 || body.includes('BadDeviceToken') || body.includes('Unregistered') ? 'invalid' : 'error');
		});
		req.end(payload);
	});
}
