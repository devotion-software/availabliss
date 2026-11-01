import { api, type Fetch } from './api';
import type { BusyInterval, User } from '#lib/types';

export type MePatch = Partial<
	Pick<User, 'name' | 'themeMode' | 'colorTheme' | 'country' | 'subdivision' | 'schoolAuthority' | 'timezone' | 'onboarded'>
>;

export const getMe = (fetch?: Fetch) => api.get<User>('/me', { fetch });
export const updateMe = (patch: MePatch) => api.patch<User>('/me', patch);
export const deleteMe = () => api.del('/me');

/** Your own merged busy time between two ISO instants (the server rejects bare dates). */
export const getMyBusy = (from: string, to: string, fetch?: Fetch) =>
	api.get<{ intervals: BusyInterval[] }>('/me/busy', { fetch, query: { from, to } }).then((r) => r.intervals);
