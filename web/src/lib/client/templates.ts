/** Saved timing templates (GET/POST /me/templates, PATCH/DELETE /me/templates/:id). */
import { api, type Fetch } from './api';
import type { Requirement, RequirementTemplate } from '#lib/types';
import { addDays, daysBetween } from './dates';

export const listTemplates = (fetch?: Fetch) =>
	api.get<{ templates: RequirementTemplate[] }>('/me/templates', { fetch }).then((r) => r.templates);
export const createTemplate = (name: string, requirement: Requirement) =>
	api.post<RequirementTemplate>('/me/templates', { name, requirement });
export const renameTemplate = (id: string, name: string) => api.patch<RequirementTemplate>(`/me/templates/${id}`, { name });
export const deleteTemplate = (id: string) => api.del(`/me/templates/${id}`);

/**
 * A template's timing, ready for a new group: same rules, but the search range
 * starts today (keeping its length), specific dates already over are dropped,
 * and times are read in your time zone.
 */
export function requirementFromTemplate(template: Pick<RequirementTemplate, 'requirement'>, today: string, timeZone: string): Requirement {
	// A JSON copy: the template may be a Svelte state proxy, which structuredClone rejects.
	const r = JSON.parse(JSON.stringify(template.requirement)) as Requirement;
	const length = Math.max(0, daysBetween(r.range.from, r.range.to));
	return {
		...r,
		range: { from: today, to: addDays(today, length) },
		dates: r.dates.filter((d) => d.to >= today),
		customSchoolHolidays: r.customSchoolHolidays.filter((d) => d.to >= today),
		timezone: timeZone
	};
}
