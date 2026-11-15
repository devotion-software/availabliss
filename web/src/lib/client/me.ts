import { api, type Fetch } from './api';
import type { BusyInterval, User } from '#lib/types';

export type MePatch = Partial<
	Pick<User, 'name' | 'themeMode' | 'colorTheme' | 'country' | 'subdivision' | 'schoolAuthority' | 'timezone' | 'onboarded'> & { username: string }
>;

export const getMe = (fetch?: Fetch) => api.get<User>('/me', { fetch });
export const updateMe = (patch: MePatch) => api.patch<User>('/me', patch);
export const deleteMe = () => api.del('/me');

/** Your own merged busy time between two ISO instants (the server rejects bare dates). */
export const getMyBusy = (from: string, to: string, fetch?: Fetch) =>
	api.get<{ intervals: BusyInterval[] }>('/me/busy', { fetch, query: { from, to } }).then((r) => r.intervals);

/** Sets your profile picture (the server shrinks it to fit 512 px). */
export const setAvatar = (file: Blob) => api.put<User>('/me/avatar', file);
/** Removes your profile picture. */
export const removeAvatar = () => api.del('/me/avatar');
