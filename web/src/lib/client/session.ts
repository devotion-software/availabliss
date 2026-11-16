import { goto } from '$app/navigation';
import { applyAppearance } from './appearance';

/**
 * Signed-out pages follow the device (as SSR renders them), not the last
 * account's choice, which would otherwise stay on <html> after a client-side
 * navigation away from the app.
 */
export function resetAppearance(): void {
	applyAppearance({ themeMode: 'system', colorTheme: 'dawn' });
}

/**
 * Signs out of Better Auth and returns to the landing page. The auth client is
 * imported on demand so it (and the passkey plugin) stays out of every /app page.
 */
export async function signOutAndLeave(): Promise<void> {
	const { authClient } = await import('#lib/auth-client');
	await authClient.signOut();
	resetAppearance();
	await goto('/', { invalidateAll: true });
}
