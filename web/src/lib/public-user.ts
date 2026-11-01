import { COLOR_THEMES, THEME_MODES, type User } from './types';
import type { User as AuthUser } from './auth';

/** The Better Auth session user as the API's public `User` shape. */
export function toPublicUser(u: AuthUser): User {
	return {
		id: u.id,
		name: u.name,
		email: u.email,
		image: u.image ?? null,
		themeMode: THEME_MODES.find((m) => m === u.themeMode) ?? 'system',
		colorTheme: COLOR_THEMES.find((t) => t === u.colorTheme) ?? 'dawn',
		country: u.country ?? null,
		subdivision: u.subdivision ?? null,
		schoolAuthority: u.schoolAuthority ?? null,
		timezone: u.timezone || 'Europe/London',
		onboarded: !!u.onboarded
	};
}
