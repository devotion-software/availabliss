import { api } from './api';
import type { PlannerResult } from '#lib/types';

/** The optional AI planner (Pro). */
export const planWithAi = (groupId: string, notes: string | null) => api.post<PlannerResult>(`/groups/${groupId}/planner`, { notes });
