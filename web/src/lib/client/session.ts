import { goto } from '$app/navigation';

/**
 * Signs out of Better Auth and returns to the landing page. The auth client is
 * imported on demand so it (and the passkey plugin) stays out of every /app page.
 */
export async function signOutAndLeave(): Promise<void> {
	const { authClient } = await import('#lib/auth-client');
	await authClient.signOut();
	await goto('/', { invalidateAll: true });
}
