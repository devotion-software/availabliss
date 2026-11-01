import type { User } from '#lib/types';

export interface RegionValue {
	country: string;
	subdivision: string;
	schoolAuthority: string;
	timezone: string;
}

export const regionFromUser = (u: Pick<User, 'country' | 'subdivision' | 'schoolAuthority' | 'timezone'>): RegionValue => ({
	country: u.country ?? '',
	subdivision: u.subdivision ?? '',
	schoolAuthority: u.schoolAuthority ?? '',
	timezone: u.timezone
});

/** The PATCH /me body for a region form (empty selects become null). */
export const regionPatch = (r: RegionValue) => ({
	country: r.country || null,
	subdivision: r.subdivision || null,
	schoolAuthority: r.schoolAuthority || null,
	timezone: r.timezone
});
