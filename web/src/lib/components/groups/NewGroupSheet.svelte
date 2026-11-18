<script lang="ts">
	import { goto, invalidate } from '$app/navigation';
	import { TILE_COLORS, type Group, type Member, type Requirement, type RequirementTemplate, type TileColor, type User } from '#lib/types';
	import { createGroupSchema, requirementSchema } from '#lib/schemas';
	import { createGroup, createInvite } from '#lib/client/groups';
	import { ApiError } from '#lib/client/api';
	import { getPlan } from '#lib/client/plan';
	import { upgradeSheet } from '#lib/client/ui-state.svelte';
	import { FREE_GROUP_LIMIT } from '#lib/plans';
	import { toasts } from '#lib/client/toast.svelte';
	import { useFormat } from '#lib/client/format';
	import { defaultRequirement } from '#lib/client/requirements';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Chip from '#lib/components/ui/Chip.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import GroupColorPicker from './GroupColorPicker.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import StepDots from '#lib/components/onboarding/StepDots.svelte';
	import RequirementsEditor from '#lib/components/timing/RequirementsEditor.svelte';
	import FitPreview from '#lib/components/timing/FitPreview.svelte';
	import SaveTemplate from '#lib/components/timing/SaveTemplate.svelte';
	import { listTemplates, requirementFromTemplate } from '#lib/client/templates';
	import { monogram } from '#lib/monogram';
	import Avatar from '#lib/components/ui/Avatar.svelte';

	/**
	 * New group in two steps: 1) name, colour and optionally a saved template;
	 * 2) the full timing editor (days, times, holidays, date range) with a live
	 * count of how many times fit, before the group is created.
	 *
	 * "New group with these people" (`basedOn`, from a group's More menu or settings) starts from
	 * that group: its name with a 2, its colour and its timing, plus a step between the two listing
	 * its other members, all ticked. Each one still ticked gets an invite to accept once it's made.
	 */
	interface Props {
		open: boolean;
		user: Pick<User, 'id' | 'timezone' | 'country'>;
		basedOn?: Group | null;
	}
	let { open = $bindable(), user, basedOn = null }: Props = $props();

	const fmt = useFormat();

	type Step = 'details' | 'people' | 'timing';
	let step = $state<Step>('details');
	/** The other group's members (not you), and who of them gets an invite. */
	let people = $state<Member[]>([]);
	let invitees = $state<Set<string>>(new Set());
	/** The timing is the other group's (picked to start with; a template replaces it). */
	let copiesTiming = $state(false);
	const steps = $derived<Step[]>(people.length ? ['details', 'people', 'timing'] : ['details', 'timing']);
	const stepIndex = $derived(steps.indexOf(step));
	let name = $state('');
	let color = $state<TileColor>('t1');
	/** Your saved timings; picking one starts step 2 from it instead of the default. */
	let templates = $state<RequirementTemplate[]>([]);
	let templateId = $state<string | null>(null);
	let requirement = $state<Requirement>(defaultRequirement('UTC', '2000-01-01'));
	let error = $state<string | null>(null);
	let busy = $state(false);
	/** Free accounts at their group limit see an upgrade prompt instead of the form. */
	let atLimit = $state(false);

	// Fresh form each time the sheet opens (from another group: its name with a 2, colour, timing and people).
	$effect.pre(() => {
		if (!open) return;
		step = 'details';
		name = basedOn ? nextName(basedOn.name) : '';
		color = basedOn?.color ?? TILE_COLORS[Math.floor(Math.random() * TILE_COLORS.length)];
		templateId = null;
		copiesTiming = !!basedOn;
		requirement = basedOn ? copiedTiming(basedOn) : defaultRequirement(user.timezone, fmt.current.today(), 'weekend');
		const others = basedOn?.members.filter((m) => m.userId !== user.id) ?? [];
		people = others;
		invitees = new Set(others.map((m) => m.userId));
		error = null;
	});

	/** "Book club" → "Book club 2", "Book club 2" → "Book club 3". */
	function nextName(old: string): string {
		const trimmed = old.trim();
		const m = /^(.*) (\d{1,3})$/.exec(trimmed);
		return m ? `${m[1]} ${Number(m[2]) + 1}` : `${trimmed} 2`.slice(0, 80);
	}

	const copiedTiming = (group: Group) => requirementFromTemplate(group, fmt.current.today(), user.timezone);

	/** The other group's timing, or (tapped again) the default. */
	function toggleCopiedTiming() {
		if (!basedOn) return;
		copiesTiming = !copiesTiming;
		templateId = null;
		requirement = copiesTiming ? copiedTiming(basedOn) : defaultRequirement(user.timezone, fmt.current.today(), 'weekend');
	}

	function toggleInvitee(id: string) {
		const next = new Set(invitees);
		if (next.has(id)) next.delete(id);
		else next.add(id);
		invitees = next;
	}

	$effect(() => {
		if (!open) return;
		let stale = false;
		listTemplates()
			.then((list) => !stale && (templates = list))
			.catch(() => {});
		return () => {
			stale = true;
		};
	});

	// Tapping the selected template again goes back to the default timing.
	function pickTemplate(t: RequirementTemplate) {
		if (templateId === t.id) {
			templateId = null;
			requirement = defaultRequirement(user.timezone, fmt.current.today(), 'weekend');
			return;
		}
		templateId = t.id;
		copiesTiming = false;
		requirement = requirementFromTemplate(t, fmt.current.today(), user.timezone);
	}

	$effect(() => {
		if (!open) return;
		let stale = false;
		getPlan()
			.then((plan) => !stale && (atLimit = !plan.canCreateGroup))
			.catch(() => {});
		return () => {
			stale = true;
		};
	});

	const tileLetters = $derived(monogram(name));

	function next(e: SubmitEvent) {
		e.preventDefault();
		const parsed = createGroupSchema.pick({ name: true }).safeParse({ name });
		if (!parsed.success) {
			error = parsed.error.issues[0]?.message ?? 'Give your group a name';
			return;
		}
		error = null;
		step = steps[stepIndex + 1] ?? 'timing';
	}

	const back = () => (step = steps[Math.max(stepIndex - 1, 0)]);

	function upgrade() {
		open = false;
		upgradeSheet.showFor(`You've used your ${FREE_GROUP_LIMIT} free groups. Pro gives you as many as you like.`);
	}

	async function create() {
		const req = { ...($state.snapshot(requirement) as Requirement), timezone: requirement.timezone || user.timezone };
		const timing = requirementSchema.safeParse(req);
		if (!timing.success) {
			toasts.error(timing.error.issues[0]?.message ?? 'Check the timing');
			return;
		}
		busy = true;
		try {
			const group = await createGroup({ name: name.trim(), color, requirement: req });
			// Keep the people's order from the other group.
			const userIds = people.map((m) => m.userId).filter((id) => invitees.has(id));
			if (userIds.length) {
				await createInvite(group.id, [], [], userIds).catch((err) =>
					toasts.error(
						`${group.name} was made, but the invites didn't send (${err instanceof Error ? err.message : 'something went wrong'}). You can invite people from Members.`
					)
				);
			}
			open = false;
			await invalidate('app:groups');
			await goto(`/app/groups/${group.id}`);
		} catch (err) {
			if (err instanceof ApiError && err.code === 'upgrade_required') atLimit = true;
			else if (err instanceof ApiError && err.issue('name')) {
				error = err.issue('name')!;
				step = 'details';
			} else toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<Sheet
	bind:open
	title={atLimit || step === 'details'
		? basedOn
			? 'New group with these people'
			: 'New group'
		: step === 'people'
			? 'Who’s in?'
			: 'When are you looking for?'}
	description={atLimit
		? undefined
		: step === 'details'
			? basedOn
				? `Starting from ${basedOn.name}. Name it and pick a colour; you’ll choose who to invite next.`
				: 'Name it and pick a colour. Timing comes next.'
			: step === 'people'
				? `Everyone ticked gets an invite to ${name.trim()} to accept. Untick anyone you’d rather leave out.`
				: `Fine-tune the timing for ${name.trim()}. You can change it later.`}
	width={step === 'timing' && !atLimit ? 640 : 480}
>
	{#snippet lead()}
		{#if !atLimit}<StepDots total={steps.length} step={stepIndex + 1} />{/if}
	{/snippet}
	{#if atLimit}
		<div class="limit">
			<span class="limit-icon"><Icon name="groups" size={22} /></span>
			<h3>You've made {FREE_GROUP_LIMIT} groups</h3>
			<p>
				Free accounts can create {FREE_GROUP_LIMIT}. Upgrade to Pro for unlimited groups, or delete one you no longer need. You can still join groups
				other people make.
			</p>
			<Button variant="accent" size="lg" block icon="sparkle" onclick={upgrade}>See Pro</Button>
		</div>
	{:else if step === 'details'}
		<form class="form" onsubmit={next} novalidate>
			<div class="name-row">
				<GroupTile monogram={tileLetters} {color} size={50} />
				<TextField label="Group name" bind:value={name} placeholder="e.g. Lake District crew" maxlength={80} {error} autofocus />
			</div>

			<GroupColorPicker value={color} onchange={(c) => (color = c)} />

			{#if templates.length || basedOn}
				<fieldset>
					<legend class="eyebrow">{basedOn ? 'Timing' : 'Start from a template'}</legend>
					<div class="chips">
						{#if basedOn}
							<Chip selected={copiesTiming} onclick={toggleCopiedTiming}>Same as {basedOn.name}</Chip>
						{/if}
						{#each templates as t (t.id)}
							<Chip selected={templateId === t.id} onclick={() => pickTemplate(t)}>{t.name}</Chip>
						{/each}
					</div>
				</fieldset>
			{/if}

			<Button type="submit" variant="accent" size="lg" block icon="arrow-right">{steps[1] === 'people' ? 'Next: people' : 'Next: timing'}</Button>
		</form>
	{:else if step === 'people'}
		<div class="list" role="group" aria-label="Who to invite">
			{#each people as m (m.userId)}
				{@const on = invitees.has(m.userId)}
				<button type="button" role="checkbox" aria-checked={on} class:sel={on} onclick={() => toggleInvitee(m.userId)}>
					<Avatar name={m.name} id={m.userId} image={m.image} size={34} />
					<span class="name">{m.name}</span>
					<span class="mark" aria-hidden="true">
						{#if on}<Icon name="check" size={16} />{/if}
					</span>
				</button>
			{/each}
		</div>
	{:else}
		<div class="form">
			<RequirementsEditor bind:requirement country={user.country} />
			<SaveTemplate {requirement} onsaved={(t) => (templates = [...templates, t])} />
		</div>
	{/if}

	{#snippet footer()}
		{#if !atLimit && step === 'people'}
			<div class="bar-actions">
				<Button variant="secondary" icon="chevron-left" onclick={back}>Back</Button>
				<Button variant="accent" icon="arrow-right" onclick={() => (step = 'timing')}>Next: timing</Button>
			</div>
		{/if}
		{#if !atLimit && step === 'timing'}
			<div class="bar">
				<FitPreview {requirement} timeZone={user.timezone} />
				<div class="bar-actions">
					<Button variant="secondary" icon="chevron-left" onclick={back}>Back</Button>
					<Button variant="accent" loading={busy} onclick={create}>Create group</Button>
				</div>
			</div>
		{/if}
	{/snippet}
</Sheet>

<style>
	.form {
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.name-row {
		display: flex;
		align-items: flex-end;
		gap: 12px;
	}
	.name-row :global(.field) {
		flex: 1;
	}
	fieldset {
		border: 0;
		padding: 0;
		margin: 0;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}
	.bar {
		width: 100%;
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		flex-wrap: wrap;
	}
	.bar-actions {
		display: flex;
		gap: 8px;
		margin-left: auto;
	}
	.list {
		display: flex;
		flex-direction: column;
		border-radius: 20px;
		border: 1px solid var(--line);
		padding: 0 12px;
	}
	.list button {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 56px;
		padding: 8px 0;
		background: none;
		border: 0;
		color: inherit;
		font: inherit;
		text-align: left;
		cursor: pointer;
	}
	.list button + button {
		border-top: 1px solid var(--line);
	}
	.name {
		flex: 1;
		min-width: 0;
		font-size: 15px;
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.mark {
		width: 22px;
		height: 22px;
		border-radius: 50%;
		border: 1.5px solid var(--line-2);
		display: grid;
		place-items: center;
		flex-shrink: 0;
	}
	.sel .mark {
		background: var(--accent);
		border-color: var(--accent);
		color: var(--on-accent);
	}
	.limit {
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
		gap: 10px;
		padding: 8px 4px 4px;
	}
	.limit-icon {
		width: 52px;
		height: 52px;
		border-radius: 18px;
		display: grid;
		place-items: center;
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	.limit h3 {
		font-size: 18px;
		font-weight: 700;
	}
	.limit p {
		font-size: 14px;
		color: var(--muted);
		line-height: 1.45;
		margin-bottom: 6px;
	}
</style>
