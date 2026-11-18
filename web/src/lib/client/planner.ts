import { api } from './api';
import type { PlannerResult } from '#lib/types';

/** The optional AI planner (Pro). */
export const planWithAi = (groupId: string, notes: string | null) => api.post<PlannerResult>(`/groups/${groupId}/planner`, { notes });

/** An AI-drafted proposal title and message for a time (Pro; `404` when AI isn't set up here). */
export const draftWithAi = (groupId: string, start: string, end: string) =>
	api.post<{ title: string; note: string }>(`/groups/${groupId}/planner/draft`, { start, end });
