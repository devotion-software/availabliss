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
	// Who needs to be free is about one group's members, so it never carries over.
	delete r.quorum;
	delete r.optionalMemberIds;
	return {
		...r,
		range: { from: today, to: addDays(today, length) },
		dates: r.dates.filter((d) => d.to >= today),
		customSchoolHolidays: r.customSchoolHolidays.filter((d) => d.to >= today),
		timezone: timeZone
	};
}

/** Key-order-independent JSON, so a timing from the server compares equal to the same one built here. */
function stable(value: unknown): string {
	if (Array.isArray(value)) return `[${value.map(stable).join(',')}]`;
	if (value && typeof value === 'object') {
		const entries = Object.entries(value as Record<string, unknown>)
			.filter(([, v]) => v !== undefined)
			.sort(([a], [b]) => (a < b ? -1 : 1));
		return `{${entries.map(([k, v]) => `${JSON.stringify(k)}:${stable(v)}`).join(',')}}`;
	}
	return JSON.stringify(value ?? null);
}

/**
 * True when `a` and `b` are the same timing, ignoring when the search range
 * starts (its length still counts) and the time zone: a template's range moves
 * to "today" when it's used. Mirrors `Requirement.isSameTiming` on iOS and Android.
 */
export function sameTiming(a: Requirement, b: Requirement): boolean {
	// Who needs to be free belongs to one group's members, and templates never keep it.
	const normalize = ({ quorum: _q, optionalMemberIds: _o, ...r }: Requirement) => ({
		...r,
		range: daysBetween(r.range.from, r.range.to),
		days: [...new Set(r.days)].sort((x, y) => x - y),
		timezone: ''
	});
	return stable(normalize(a)) === stable(normalize(b));
}

/** The template with the same timing as `requirement`, if one was saved. */
export const matchingTemplate = (templates: RequirementTemplate[], requirement: Requirement) =>
	templates.find((t) => sameTiming(t.requirement, requirement));
