import { describe, expect, test } from 'bun:test';
import { formatAccuracy, locationLinks, locationSummary, mapTiles, toPlace } from './location';

const dishoom = { lat: 51.5245456789, lng: -0.076855, name: 'Dishoom Shoreditch', address: null, accuracy: null };

describe('shared places', () => {
	test('links carry the coordinates (and the name for Apple Maps)', () => {
		expect(locationLinks(dishoom)).toEqual({
			apple: 'https://maps.apple.com/?ll=51.524546,-0.076855&q=Dishoom%20Shoreditch',
			google: 'https://www.google.com/maps/search/?api=1&query=51.524546,-0.076855',
			what3words: 'https://what3words.com/51.524546,-0.076855'
		});
		expect(locationSummary({ name: null })).toBe('📍 Location');
	});

	test('map tiles cover the view, centred on the place', () => {
		const tiles = mapTiles(51.5245, -0.0769, 240, 130, 16, 128);
		expect(tiles.length).toBeGreaterThanOrEqual(4);
		expect(tiles.every((t) => t.url.startsWith('https://tile.openstreetmap.org/16/'))).toBe(true);
		// The view is covered: some tile starts at or before its top-left corner.
		expect(tiles.some((t) => t.left <= 0 && t.top <= 0)).toBe(true);
	});

	test('search results read as a name and a short address', () => {
		expect(
			toPlace({
				lat: '51.5245',
				lon: '-0.0769',
				name: 'Dishoom',
				display_name: 'Dishoom, 7, Boundary Street, Shoreditch, London Borough of Hackney, London, England, E2 7JE, United Kingdom'
			})
		).toEqual({ name: 'Dishoom', address: '7 Boundary Street, Shoreditch, London Borough of Hackney, London', lat: 51.5245, lng: -0.0769 });
		expect(toPlace({ lat: 'x', lon: '0' })).toBeNull();
		expect(formatAccuracy(12.4)).toBe('12 m');
		expect(formatAccuracy(1500)).toBe('1.5 km');
	});
});
