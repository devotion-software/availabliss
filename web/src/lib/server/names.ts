/**
 * The name other people see for someone: the name they go by in the group (`nameInGroup`,
 * when the row comes from a group and they've set one), else their account name, or their
 * email address when they haven't set one (email sign-up creates accounts with an empty name).
 */
export function displayName(
	row: { name: string | null; email: string | null; nameInGroup?: string | null } | null | undefined,
	fallback = 'Someone'
): string {
	return row?.nameInGroup?.trim() || row?.name?.trim() || row?.email?.trim() || fallback;
}
