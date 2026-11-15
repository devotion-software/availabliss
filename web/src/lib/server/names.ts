/**
 * The name other people see for someone: their name, or their email address when they
 * haven't set one (email sign-up creates accounts with an empty name).
 */
export function displayName(row: { name: string | null; email: string | null } | null | undefined, fallback = 'Someone'): string {
	return row?.name?.trim() || row?.email?.trim() || fallback;
}
