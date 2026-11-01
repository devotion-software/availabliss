import type { CalendarProvider } from '#lib/types';

export const PROVIDER_NAME: Record<CalendarProvider, string> = {
	google: 'Google',
	microsoft: 'Outlook',
	caldav: 'iCloud',
	device: 'On a device'
};

export const PROVIDER_BRAND: Record<CalendarProvider, 'google' | 'microsoft' | 'icloud' | null> = {
	google: 'google',
	microsoft: 'microsoft',
	caldav: 'icloud',
	device: null
};

/** Fallback swatch when a calendar has no colour of its own. */
export const FALLBACK_COLORS = ['var(--warn)', '#6FD8BB', '#C9A6F5', '#5B8DEF', '#F2C14E'];
