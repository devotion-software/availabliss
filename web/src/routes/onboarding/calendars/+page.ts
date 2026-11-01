import { listConnections } from '#lib/client/calendars';
import { listCountries, listSchoolAuthorities, listSubdivisions } from '#lib/client/regions';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, parent }) => {
	const { user } = await parent();
	const country = user?.country;
	const [connections, regionLabel] = await Promise.all([
		listConnections(fetch).catch(() => []),
		country ? describeRegion(country, user?.subdivision ?? null, user?.schoolAuthority ?? null, fetch) : null
	]);
	return { connections, regionLabel };
};

/** "United Kingdom · England" + "School holidays: Bristol City Council" for the region card. */
async function describeRegion(country: string, subdivision: string | null, authority: string | null, fetch: typeof globalThis.fetch) {
	const [countries, subdivisions, authorities] = await Promise.all([
		listCountries(fetch).catch(() => []),
		subdivision ? listSubdivisions(country, fetch).catch(() => []) : [],
		authority ? listSchoolAuthorities(country, fetch).catch(() => []) : []
	]);
	const place = [countries.find((c) => c.code === country)?.name ?? country, subdivisions.find((s) => s.code === subdivision)?.name]
		.filter(Boolean)
		.join(' · ');
	const school = authorities.find((a) => a.id === authority)?.name ?? null;
	return { place, school };
}
