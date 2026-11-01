import { api, type Fetch } from './api';
import type { Availability, DayAvailability } from '#lib/types';

export const getAvailability = (groupId: string, from: string, to: string, fetch?: Fetch) =>
	api.get<Availability>(`/groups/${groupId}/availability`, { fetch, query: { from, to } });

export const getDayAvailability = (groupId: string, date: string, fetch?: Fetch) =>
	api.get<DayAvailability>(`/groups/${groupId}/availability/day`, { fetch, query: { date } });
