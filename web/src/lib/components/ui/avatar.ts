import { TILE_COLORS, type TileColor } from '#lib/types';

/** A stable tile colour for a person, so they look the same everywhere. */
export function tileFor(seed: string): TileColor {
	let h = 0;
	for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) | 0;
	return TILE_COLORS[Math.abs(h) % TILE_COLORS.length];
}

export interface Person {
	id: string;
	name: string;
	image?: string | null;
}
