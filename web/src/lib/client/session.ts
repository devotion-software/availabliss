import { goto } from '$app/navigation';
import { authClient } from '#lib/auth-client';

/** Signs out of Better Auth and returns to the landing page. */
export async function signOutAndLeave(): Promise<void> {
	await authClient.signOut();
	await goto('/', { invalidateAll: true });
}
