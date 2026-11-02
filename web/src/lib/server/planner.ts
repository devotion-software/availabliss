/**
 * The optional AI planner (Pro). Given the times that already work for the
 * whole group, Claude picks the best one, explains why, and suggests things to
 * do and a short plan. Nothing runs unless someone asks for it.
 *
 * Privacy: Claude sees the group's name, its timing, the free slots, named
 * holidays, how many people are in it and the asker's own notes. Never member
 * names, calendars or anyone's busy time.
 */
import Anthropic from '@anthropic-ai/sdk';
import { betaZodOutputFormat } from '@anthropic-ai/sdk/helpers/beta/zod';
import { z } from 'zod';
import type { PlannerResult, Slot } from '#lib/types';
import { env } from './env';
import { ApiError, badRequest, conflict } from './http';
import { getGroupRow, requireMember } from './groups';
import { isPro } from './billing';
import { planningContext } from './availability/service';
import { requirementSummary } from './availability/labels';

export const aiPlannerConfigured = () => !!env.ai.apiKey;

let client: Anthropic | null = null;
const anthropic = () => (client ??= new Anthropic({ apiKey: env.ai.apiKey!, maxRetries: 2, timeout: 120_000 }));

/** What Claude must return. Slots are referenced by their number so they can't be invented. */
export const PlanSchema = z.object({
	best: z.object({ slot: z.number().int(), why: z.string() }),
	alternatives: z.array(z.object({ slot: z.number().int(), why: z.string() })),
	ideas: z.array(z.object({ title: z.string(), description: z.string() })),
	checklist: z.array(z.string()),
	proposalTitle: z.string(),
	proposalNote: z.string()
});
export type Plan = z.infer<typeof PlanSchema>;

const SYSTEM = `You help a group of friends or family pick a time to meet and plan what to do. You're given the times that already work for everyone in the group (each with a number), the kind of time they're looking for, upcoming holidays and optional notes from the person asking.

Choose the best time and explain the choice in one or two plain sentences, thinking about things like: enough notice to organise, holidays and long weekends, not leaving it too late, and anything in the notes. Offer up to three alternatives with a short reason each. Suggest three to five things to do that fit the length of time, the season and the notes; keep each description to one sentence. Give a short checklist of three to six concrete next steps. Write a proposal title of at most 60 characters and a friendly proposal note of at most 400 characters that the group will read.

Only refer to times by their number from the list. Use British English. Don't invent facts about specific venues, prices or opening times; keep ideas general unless the notes name a place.`;

/** Keeps references inside the list and drops duplicates; throws if the model picked nothing usable. */
export function resolvePlan(plan: Plan, slots: Slot[]): PlannerResult {
	const at = (n: number) => slots[n - 1] ?? null;
	const best = at(plan.best.slot);
	if (!best) throw new ApiError('server_error', 'The planner returned an unknown time. Please try again.');
	const seen = new Set([plan.best.slot]);
	const alternatives = plan.alternatives
		.filter((a) => at(a.slot) && !seen.has(a.slot) && seen.add(a.slot))
		.slice(0, 3)
		.map((a) => ({ slot: at(a.slot)!, why: a.why }));
	return {
		best: { slot: best, why: plan.best.why },
		alternatives,
		ideas: plan.ideas.slice(0, 5),
		checklist: plan.checklist.slice(0, 6),
		proposalTitle: plan.proposalTitle.slice(0, 120),
		proposalNote: plan.proposalNote.slice(0, 1000)
	};
}

export function buildPrompt(input: {
	groupName: string;
	timing: string;
	today: string;
	timeZone: string;
	members: number;
	synced: number;
	slots: Slot[];
	holidays: { date: string; name: string }[];
	notes: string | null;
}): string {
	const lines = [
		`Group: ${input.groupName} (${input.members} people, ${input.synced} sharing their availability)`,
		`Looking for: ${input.timing}`,
		`Today: ${input.today} (times are in ${input.timeZone})`,
		'',
		'Times that work for everyone:',
		...input.slots.map((s, i) => `${i + 1}. ${s.label} (${s.start} to ${s.end})`),
		'',
		input.holidays.length ? `Holidays in this period: ${input.holidays.map((h) => `${h.date} ${h.name}`).join('; ')}` : 'No public holidays in this period.'
	];
	if (input.notes) lines.push('', `Notes from the person asking: ${input.notes}`);
	return lines.join('\n');
}

export async function planWithAi(groupId: string, userId: string, notes: string | null): Promise<PlannerResult> {
	await requireMember(groupId, userId);
	if (!aiPlannerConfigured()) throw new ApiError('not_found', 'AI planning isn’t available');
	if (!(await isPro(userId))) throw new ApiError('upgrade_required', 'AI planning is part of Availabliss Pro.');

	const group = await getGroupRow(groupId);
	const context = await planningContext(group);
	if (!context.slots.length) throw conflict('No times work for everyone yet, so there’s nothing to plan around. Try widening the timing.');

	const prompt = buildPrompt({
		groupName: group.name,
		timing: requirementSummary(group.requirement),
		today: context.from,
		timeZone: group.requirement.timezone,
		members: context.members,
		synced: context.synced,
		slots: context.slots,
		holidays: context.holidays,
		notes
	});

	let response;
	try {
		response = await anthropic().beta.messages.parse({
			model: env.ai.model,
			max_tokens: 16000,
			// Planning advice, not deep reasoning: medium effort keeps it quick.
			output_config: { effort: 'medium', format: betaZodOutputFormat(PlanSchema) },
			// If a safety classifier declines, the API retries on a suitable model in the same call.
			betas: ['server-side-fallback-2026-07-01'],
			fallbacks: 'default',
			system: SYSTEM,
			messages: [{ role: 'user', content: prompt }]
		});
	} catch (err) {
		if (err instanceof Anthropic.RateLimitError) throw new ApiError('rate_limited', 'The planner is busy. Try again in a minute.');
		if (err instanceof Anthropic.BadRequestError) {
			console.error('[planner] bad request', err.message);
			throw new ApiError('server_error', 'The planner couldn’t take that request. Please try again.');
		}
		if (err instanceof Anthropic.APIError) {
			console.error('[planner] API error', err.status, err.message);
			throw new ApiError('server_error', 'The planner is unavailable right now. Please try again shortly.');
		}
		throw err;
	}

	if (response.stop_reason === 'refusal') throw badRequest('The planner can’t help with that. Try different notes.');
	if (response.stop_reason === 'max_tokens' || !response.parsed_output) {
		console.error('[planner] no structured output', response.stop_reason);
		throw new ApiError('server_error', 'The planner didn’t finish. Please try again.');
	}
	return resolvePlan(response.parsed_output, context.slots);
}
