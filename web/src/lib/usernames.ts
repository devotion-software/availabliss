/**
 * Usernames: a unique handle people can be invited by instead of their email.
 * Stored lowercase; typed with or without a leading "@". The apps mirror these rules.
 */
export const USERNAME_MIN = 3;
export const USERNAME_MAX = 20;

const RESERVED = new Set([
	'admin',
	'administrator',
	'availabliss',
	'help',
	'me',
	'moderator',
	'null',
	'root',
	'support',
	'system',
	'undefined',
	'you'
]);

/** What was typed, as stored: no "@", trimmed, lowercase. */
export const normalizeUsername = (raw: string) => raw.trim().replace(/^@+/, '').toLowerCase();

/** Why a (normalized) username can't be used, or null when it's fine. */
export function usernameProblem(username: string): string | null {
	if (username.length < USERNAME_MIN) return `At least ${USERNAME_MIN} characters`;
	if (username.length > USERNAME_MAX) return `At most ${USERNAME_MAX} characters`;
	if (!/^[a-z0-9_.]+$/.test(username)) return 'Only letters, numbers, _ and .';
	if (username.startsWith('.') || username.endsWith('.') || username.includes('..')) return "A dot can't come first, last or twice in a row";
	if (RESERVED.has(username)) return "That one's reserved";
	return null;
}

/** A starting point made from someone's name or email: "Sam Taylor" → "samtaylor". */
export function usernameBase(name: string | null | undefined, email: string | null | undefined): string {
	const from = (s: string) =>
		s
			.normalize('NFKD')
			.replace(/[̀-ͯ]/g, '')
			.toLowerCase()
			.replace(/[^a-z0-9_]/g, '')
			.slice(0, USERNAME_MAX - 4);
	const base = from(name ?? '') || from((email ?? '').split('@')[0]);
	return base.length >= USERNAME_MIN ? base : `${base}user`.slice(0, USERNAME_MAX - 4);
}
