import { describe, expect, test } from 'bun:test';
import { bankHolidays, detectRegion, schoolHolidayRanges } from './holidays';

const headers = (h: Record<string, string>) => new Headers(h);

describe('detectRegion', () => {
	test('prefers CDN country headers, upper-cased', () => {
		expect(detectRegion(headers({ 'cf-ipcountry': 'fr', 'accept-language': 'en-GB' }))).toEqual({
			country: 'FR',
			subdivision: null,
			timezone: null
		});
		expect(detectRegion(headers({ 'x-vercel-ip-country': 'DE' })).country).toBe('DE');
		expect(detectRegion(headers({ 'x-country-code': 'US' })).country).toBe('US');
	});

	test('falls back to the browser language region', () => {
		expect(detectRegion(headers({ 'accept-language': 'en-GB,en;q=0.9' }))).toEqual({
			country: 'GB',
			subdivision: 'GB-ENG',
			timezone: null
		});
		expect(detectRegion(headers({ 'cf-ipcountry': 'T1', 'accept-language': 'de-AT' })).country).toBe('AT');
		// Cloudflare's "unknown" isn't a country either.
		expect(detectRegion(headers({ 'cf-ipcountry': 'XX', 'accept-language': 'fr-BE' })).country).toBe('BE');
	});

	test('nothing known', () => {
		expect(detectRegion(headers({ 'accept-language': 'en' }))).toEqual({ country: null, subdivision: null, timezone: null });
	});

	test('passes the client timezone through', () => {
		expect(detectRegion(headers({ 'x-timezone': 'Europe/London' })).timezone).toBe('Europe/London');
	});
});

describe('without a database', () => {
	test('no country means no bank holidays', async () => {
		expect((await bankHolidays(null, null, { from: '2026-01-01', to: '2026-12-31' })).size).toBe(0);
	});

	test('no authority means only the custom ranges', async () => {
		const ranges = await schoolHolidayRanges(
			{ schoolAuthority: null, customSchoolHolidays: [{ from: '2026-10-26', to: '2026-10-30' }] },
			{ from: '2026-01-01', to: '2026-12-31' }
		);
		expect(ranges).toEqual([{ from: '2026-10-26', to: '2026-10-30', name: 'School holidays' }]);
	});
});
