import { json } from '@sveltejs/kit';
import { env } from '#lib/server/env';

/** Digital Asset Links for Android app links and passkeys (Credential Manager). */
export const GET = () => {
	if (!env.android.package || !env.android.sha256Fingerprints.length) return json([]);
	return json([
		{
			relation: ['delegate_permission/common.handle_all_urls', 'delegate_permission/common.get_login_creds'],
			target: {
				namespace: 'android_app',
				package_name: env.android.package,
				sha256_cert_fingerprints: env.android.sha256Fingerprints
			}
		}
	]);
};
