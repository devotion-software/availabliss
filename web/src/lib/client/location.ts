/** Shared places in chat: links to open them, OpenStreetMap tiles for their little maps, and place search. */
import type { MessageLocation } from '#lib/types';

/** Coordinates as sent in links: 6 decimal places is about 10 cm. */
const coord = (n: number) => String(Number(n.toFixed(6)));
const pair = (l: Pick<MessageLocation, 'lat' | 'lng'>) => `${coord(l.lat)},${coord(l.lng)}`;

/** "📍 Dishoom Shoreditch" or "📍 Location", as the server quotes it. */
export const locationSummary = (l: Pick<MessageLocation, 'name'>) => `📍 ${l.name || 'Location'}`;

/** Where a shared place can be opened. Each opens its app where it's installed (they handle these links), else the website. */
export const locationLinks = (l: MessageLocation) => ({
	apple: `https://maps.apple.com/?ll=${pair(l)}&q=${encodeURIComponent(l.name || 'Shared location')}`,
	google: `https://www.google.com/maps/search/?api=1&query=${pair(l)}`,
	/** what3words turns the coordinates in the link into the square's three words. */
	what3words: `https://what3words.com/${pair(l)}`
});

export interface MapTile {
	url: string;
	/** Where the tile's top-left corner sits in the view, in CSS pixels. */
	left: number;
	top: number;
}

/**
 * The OpenStreetMap tiles covering a `width` × `height` view centred on `lat`, `lng` at `zoom`,
 * each drawn `size` pixels square. No API key; the tiles need the © OpenStreetMap credit shown.
 */
export function mapTiles(lat: number, lng: number, width: number, height: number, zoom = 16, size = 128): MapTile[] {
	const n = 2 ** zoom;
	const latRad = (Math.max(-85, Math.min(85, lat)) * Math.PI) / 180;
	const cx = ((lng + 180) / 360) * n * size;
	const cy = ((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2) * n * size;
	const left = cx - width / 2;
	const top = cy - height / 2;
	const tiles: MapTile[] = [];
	for (let x = Math.floor(left / size); x <= Math.floor((left + width) / size); x++) {
		for (let y = Math.floor(top / size); y <= Math.floor((top + height) / size); y++) {
			if (y < 0 || y >= n) continue;
			const wrapped = ((x % n) + n) % n;
			tiles.push({ url: `https://tile.openstreetmap.org/${zoom}/${wrapped}/${y}.png`, left: x * size - left, top: y * size - top });
		}
	}
	return tiles;
}

export interface FoundPlace {
	name: string;
	address: string | null;
	lat: number;
	lng: number;
}

interface NominatimResult {
	lat: string;
	lon: string;
	name?: string;
	display_name?: string;
}

/** A Nominatim result as a place: its name, and a short address (no country, at most four parts). */
export function toPlace(r: NominatimResult): FoundPlace | null {
	const lat = Number(r.lat);
	const lng = Number(r.lon);
	if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
	const parts = (r.display_name ?? '').split(', ').filter(Boolean);
	const name = r.name?.trim() || parts[0];
	if (!name) return null;
	let rest = parts.slice(parts[0] === name ? 1 : 0, -1);
	// "7, Boundary Street" reads as "7 Boundary Street".
	if (rest.length >= 2 && /^\d[\w-]{0,5}$/.test(rest[0])) rest = [`${rest[0]} ${rest[1]}`, ...rest.slice(2)];
	return { name, address: rest.slice(0, 4).join(', ') || null, lat, lng };
}

/**
 * Finds places by name with OpenStreetMap's Nominatim (no API key; its policy rules out
 * search-as-you-type, so only on Search). `near` (to about 10 km) ranks nearby places first.
 */
export async function searchPlaces(query: string, near?: { lat: number; lng: number } | null): Promise<FoundPlace[]> {
	const params = new URLSearchParams({ q: query, format: 'jsonv2', limit: '6' });
	if (near) {
		const r = (n: number) => n.toFixed(1);
		params.set('viewbox', `${r(near.lng - 0.5)},${r(near.lat + 0.5)},${r(near.lng + 0.5)},${r(near.lat - 0.5)}`);
	}
	const res = await fetch(`https://nominatim.openstreetmap.org/search?${params}`, { headers: { Accept: 'application/json' } });
	if (!res.ok) throw new Error('Couldn’t search for places. Try again in a moment.');
	const results = (await res.json()) as NominatimResult[];
	return results.map(toPlace).filter((p): p is FoundPlace => p !== null);
}

/** "12 m", "1.2 km". */
export const formatAccuracy = (metres: number) => (metres < 1000 ? `${Math.round(metres)} m` : `${(metres / 1000).toFixed(1)} km`);
