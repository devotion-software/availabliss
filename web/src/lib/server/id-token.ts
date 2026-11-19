/**
 * The `email` claim of a sign-in's ID token (only read, not verified: Better Auth checked it when
 * it was issued). Apple's can be a Hide My Email relay address, which is still the one to show.
 */
export function tokenEmail(idToken: string | null): string | null {
	if (!idToken) return null;
	try {
		const payload = JSON.parse(Buffer.from(idToken.split('.')[1], 'base64url').toString('utf8'));
		return typeof payload.email === 'string' && payload.email.includes('@') ? payload.email : null;
	} catch {
		return null;
	}
}
