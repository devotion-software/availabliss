/** Only same-origin absolute paths are allowed as post-sign-in destinations. */
export function safeRedirectPath(value: string | null | undefined): string | null {
	if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return null;
	return value;
}
