/**
 * Appearance (light / dark / system and the five colour themes). Applied by
 * setting attributes on <html>; app.html resolves `data-mode` to `data-scheme`.
 */
import type { ColorTheme, ThemeMode } from '#lib/types';

export function applyAppearance(appearance: { themeMode?: ThemeMode; colorTheme?: ColorTheme }): void {
	const root = document.documentElement;
	if (appearance.themeMode) root.dataset.mode = appearance.themeMode;
	if (appearance.colorTheme) root.dataset.theme = appearance.colorTheme;
}

export const MODE_OPTIONS: readonly { value: ThemeMode; label: string; icon: 'sun' | 'moon' | 'monitor' }[] = [
	{ value: 'light', label: 'Light', icon: 'sun' },
	{ value: 'dark', label: 'Dark', icon: 'moon' },
	{ value: 'system', label: 'System', icon: 'monitor' }
];

/**
 * Swatch colours for the theme picker (docs/DESIGN.md). The live tokens are
 * scoped to <html>, so each swatch needs its own copy to preview other themes.
 */
export const THEME_SWATCHES: readonly { value: ColorTheme; label: string; accent: string; orbs: [string, string, string] }[] = [
	{ value: 'dawn', label: 'Dawn', accent: '#5546FF', orbs: ['#FFB894', '#9DA6FF', '#8EE3CC'] },
	{ value: 'lagoon', label: 'Lagoon', accent: '#0B7F8A', orbs: ['#86E3E0', '#8EC5FF', '#B5EBA8'] },
	{ value: 'sunset', label: 'Sunset', accent: '#C9401F', orbs: ['#FFA585', '#FF9CC0', '#FFD37E'] },
	{ value: 'orchid', label: 'Orchid', accent: '#9333C9', orbs: ['#F4B0E0', '#C9A8FF', '#A9B8FF'] },
	{ value: 'grove', label: 'Grove', accent: '#2D7A4A', orbs: ['#C3E88F', '#8FD8B5', '#F3D78A'] }
];
