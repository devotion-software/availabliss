import { api, type Fetch } from './api';

export interface Option {
	code: string;
	name: string;
}

export interface DetectedRegion {
	country: string | null;
	subdivision: string | null;
	timezone: string | null;
}

export const listCountries = (fetch?: Fetch) =>
	api.get<{ countries: Option[] }>('/regions/countries', { fetch }).then((r) => r.countries);

export const listSubdivisions = (country: string, fetch?: Fetch) =>
	api.get<{ subdivisions: Option[] }>('/regions/subdivisions', { fetch, query: { country } }).then((r) => r.subdivisions);

export const listSchoolAuthorities = (country: string, fetch?: Fetch) =>
	api
		.get<{ authorities: { id: string; name: string }[] }>('/regions/school-authorities', { fetch, query: { country } })
		.then((r) => r.authorities);

export const detectRegion = (fetch?: Fetch) => api.get<DetectedRegion>('/regions/detect', { fetch });

/** Every IANA zone the browser knows, falling back to the user's own. */
export function timeZones(current?: string): string[] {
	const zones = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : [];
	return current && !zones.includes(current) ? [current, ...zones] : zones;
}
