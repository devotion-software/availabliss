import { createAuthClient } from 'better-auth/svelte';
import { emailOTPClient } from 'better-auth/client/plugins';
import { passkeyClient } from '@better-auth/passkey/client';

export const authClient = createAuthClient({
	// baseURL inferred from window.location in the browser.
	plugins: [emailOTPClient(), passkeyClient()]
});

export const { signIn, signOut } = authClient;
