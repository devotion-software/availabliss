/**
 * Regions, bank holidays and school holidays.
 *
 * Bank holidays come from the free Nager.Date API (https://date.nager.at) and are
 * cached per country/year in `holiday_cache`. School holidays come from the
 * `school_holidays` table (see scripts/import-school-holidays.ts) plus any custom
 * ranges a group defines.
 */
import { and, asc, eq, gte, lte } from 'drizzle-orm';
import type { DateRange, Requirement } from '#lib/types';
import { db } from './db';
import { holidayCache, schoolHolidays } from './db/schema';
import { inRange } from './availability/time';

const NAGER = 'https://date.nager.at/api/v3';
const CACHE_MS = 30 * 24 * 60 * 60 * 1000;

type CachedHoliday = (typeof holidayCache.$inferSelect)['holidays'][number];

interface NagerHoliday {
	date: string;
	localName: string;
	name: string;
	global: boolean;
	counties: string[] | null;
}

async function fetchJson<T>(url: string): Promise<T> {
	const res = await fetch(url, { headers: { accept: 'application/json' }, signal: AbortSignal.timeout(8000) });
	if (!res.ok) throw new Error(`${url} → ${res.status}`);
	return (await res.json()) as T;
}

async function holidaysForYear(country: string, year: number): Promise<CachedHoliday[]> {
	const cached = await db.query.holidayCache.findFirst({
		where: and(eq(holidayCache.country, country), eq(holidayCache.year, year))
	});
	if (cached && Date.now() - cached.fetchedAt.getTime() < CACHE_MS) return cached.holidays;

	try {
		const list = await fetchJson<NagerHoliday[]>(`${NAGER}/PublicHolidays/${year}/${country}`);
		const holidays = list.map((h) => ({ date: h.date, name: h.name, subdivisions: h.global ? null : h.counties }));
		await db
			.insert(holidayCache)
			.values({ country, year, holidays, fetchedAt: new Date() })
			.onDuplicateKeyUpdate({ set: { holidays, fetchedAt: new Date() } });
		return holidays;
	} catch (err) {
		// Serve stale data rather than failing availability when the API is down.
		console.warn('[holidays] fetch failed', country, year, err);
		return cached?.holidays ?? [];
	}
}

/** Bank holidays by date for a country (and optional ISO 3166-2 subdivision). */
export async function bankHolidays(
	country: string | null,
	subdivision: string | null,
	range: DateRange
): Promise<Map<string, string>> {
	const result = new Map<string, string>();
	if (!country) return result;
	const years = new Set([Number(range.from.slice(0, 4)), Number(range.to.slice(0, 4))]);
	for (const year of years) {
		for (const h of await holidaysForYear(country, year)) {
			const applies = !h.subdivisions || (subdivision !== null && h.subdivisions.includes(subdivision));
			if (applies && inRange(h.date, range)) result.set(h.date, h.name);
		}
	}
	return result;
}

/** School holiday ranges for an authority, plus a group's custom ranges. */
export async function schoolHolidayRanges(
	req: Pick<Requirement, 'schoolAuthority' | 'customSchoolHolidays'>,
	range: DateRange
): Promise<(DateRange & { name: string })[]> {
	const custom = req.customSchoolHolidays.map((r) => ({ ...r, name: 'School holidays' }));
	if (!req.schoolAuthority) return custom;
	const rows = await db
		.select({ from: schoolHolidays.start, to: schoolHolidays.end, name: schoolHolidays.name })
		.from(schoolHolidays)
		.where(
			and(
				eq(schoolHolidays.authority, req.schoolAuthority),
				lte(schoolHolidays.start, range.to),
				gte(schoolHolidays.end, range.from)
			)
		);
	return [...rows, ...custom];
}

// ─── Region pickers ───────────────────────────────────────────────────────

let countryCache: { at: number; countries: { code: string; name: string }[] } | null = null;

export async function countries(): Promise<{ code: string; name: string }[]> {
	if (countryCache && Date.now() - countryCache.at < 24 * 60 * 60 * 1000) return countryCache.countries;
	const list = await fetchJson<{ countryCode: string; name: string }[]>(`${NAGER}/AvailableCountries`);
	const result = list.map((c) => ({ code: c.countryCode, name: c.name })).sort((a, b) => a.name.localeCompare(b.name));
	countryCache = { at: Date.now(), countries: result };
	return result;
}

// Nager.Date gives subdivision codes without names; name the common ones.
const SUBDIVISION_NAMES: Record<string, string> = {
	'GB-ENG': 'England',
	'GB-SCT': 'Scotland',
	'GB-WLS': 'Wales',
	'GB-NIR': 'Northern Ireland'
};

/** Subdivisions that have their own bank holidays in the current year. */
export async function subdivisions(country: string): Promise<{ code: string; name: string }[]> {
	const year = new Date().getUTCFullYear();
	const codes = new Set<string>();
	for (const h of await holidaysForYear(country, year)) h.subdivisions?.forEach((c) => codes.add(c));
	return [...codes].sort().map((code) => ({ code, name: SUBDIVISION_NAMES[code] ?? code }));
}

export async function schoolAuthorities(country: string): Promise<{ id: string; name: string }[]> {
	const rows = await db
		.selectDistinct({ id: schoolHolidays.authority, name: schoolHolidays.authorityName })
		.from(schoolHolidays)
		.where(eq(schoolHolidays.country, country))
		.orderBy(asc(schoolHolidays.authorityName));
	return rows;
}

/** Best-effort region from proxy / CDN headers and the browser's language. */
export function detectRegion(headers: Headers): { country: string | null; subdivision: string | null; timezone: string | null } {
	const fromCdn = headers.get('cf-ipcountry') ?? headers.get('x-vercel-ip-country') ?? headers.get('x-country-code');
	const fromLanguage = headers.get('accept-language')?.match(/^[a-z]{2,3}-([A-Z]{2})/)?.[1] ?? null;
	const country = (fromCdn && /^[A-Z]{2}$/i.test(fromCdn) ? fromCdn.toUpperCase() : null) ?? fromLanguage;
	return { country, subdivision: country === 'GB' ? 'GB-ENG' : null, timezone: headers.get('x-timezone') };
}

