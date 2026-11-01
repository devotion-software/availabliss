import { json } from '@sveltejs/kit';
import { env } from '#lib/server/env';

/** Associates this domain with the iOS app for passkeys (webcredentials) and universal links. */
export const GET = () => {
	const appId = env.apple.teamId && env.apple.bundleId ? `${env.apple.teamId}.${env.apple.bundleId}` : null;
	return json({
		webcredentials: { apps: appId ? [appId] : [] },
		applinks: {
			details: appId
				? [{ appIDs: [appId], components: [{ '/': '/invite/*' }, { '/': '/app/*' }] }]
				: []
		}
	});
};
