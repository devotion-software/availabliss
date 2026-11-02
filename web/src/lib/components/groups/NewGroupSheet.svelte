<script lang="ts">
	import { goto, invalidate } from '$app/navigation';
	import { TILE_COLORS, type Requirement, type RequirementTemplate, type TileColor, type User } from '#lib/types';
	import { createGroupSchema, requirementSchema } from '#lib/schemas';
	import { createGroup } from '#lib/client/groups';
	import { ApiError } from '#lib/client/api';
	import { getPlan } from '#lib/client/plan';
	import { upgradeSheet } from '#lib/client/ui-state.svelte';
	import { FREE_GROUP_LIMIT } from '#lib/plans';
	import { toasts } from '#lib/client/toast.svelte';
	import { useFormat } from '#lib/client/format';
	import { defaultRequirement, summarize, PRESETS, type PresetId } from '#lib/client/requirements';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Chip from '#lib/components/ui/Chip.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import StepDots from '#lib/components/onboarding/StepDots.svelte';
	import RequirementsEditor from '#lib/components/timing/RequirementsEditor.svelte';
	import FitPreview from '#lib/components/timing/FitPreview.svelte';
	import SaveTemplate from '#lib/components/timing/SaveTemplate.svelte';
	import { listTemplates, requirementFromTemplate } from '#lib/client/templates';

	/**
	 * New group in two steps: 1) name, colour and roughly what you're planning;
	 * 2) the full timing editor (days, times, holidays, date range) with a live
	 * count of how many times fit, before the group is created.
	 */
	interface Props {
		open: boolean;
		user: Pick<User, 'timezone' | 'country'>;
	}
	let { open = $bindable(), user }: Props = $props();

	const fmt = useFormat();
	const QUICK: PresetId[] = ['2h', 'evening', 'day', 'weekend', 'week'];

	let step = $state<1 | 2>(1);
	let name = $state('');
	let color = $state<TileColor>('t1');
	let preset = $state<PresetId>('weekend');
	/** Your saved timings; picking one replaces the preset. */
	let templates = $state<RequirementTemplate[]>([]);
	let templateId = $state<string | null>(null);
	let requirement = $state<Requirement>(defaultRequirement('UTC', '2000-01-01'));
	let error = $state<string | null>(null);
	let busy = $state(false);
	/** Free accounts at their group limit see an upgrade prompt instead of the form. */
	let atLimit = $state(false);

	// Fresh form each time the sheet opens.
	$effect.pre(() => {
		if (!open) return;
		step = 1;
		name = '';
		color = TILE_COLORS[Math.floor(Math.random() * TILE_COLORS.length)];
		preset = 'weekend';
		templateId = null;
		requirement = defaultRequirement(user.timezone, fmt.current.today(), 'weekend');
		error = null;
	});

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

	function pickTemplate(t: RequirementTemplate) {
		templateId = t.id;
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

	const monogram = $derived(
		name
			.trim()
			.split(/\s+/)
			.filter(Boolean)
			.slice(0, 2)
			.map((w) => w[0]?.toUpperCase())
			.join('') || '?'
	);

	// A fresh timing per preset: "A week" needs every day, "A weekend" Fri–Sun, and so on.
	function pickPreset(id: PresetId) {
		preset = id;
		templateId = null;
		requirement = defaultRequirement(user.timezone, fmt.current.today(), id);
	}

	function next(e: SubmitEvent) {
		e.preventDefault();
		const parsed = createGroupSchema.pick({ name: true }).safeParse({ name });
		if (!parsed.success) {
			error = parsed.error.issues[0]?.message ?? 'Give your group a name';
			return;
		}
		error = null;
		step = 2;
	}

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
			open = false;
			await invalidate('app:groups');
			await goto(`/app/groups/${group.id}`);
		} catch (err) {
			if (err instanceof ApiError && err.code === 'upgrade_required') atLimit = true;
			else if (err instanceof ApiError && err.issue('name')) {
				error = err.issue('name')!;
				step = 1;
			} else toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<Sheet
	bind:open
	title={atLimit ? 'New group' : step === 1 ? 'New group' : 'When are you looking for?'}
	description={atLimit ? undefined : step === 1 ? 'Name it and pick roughly what you’re planning.' : `Fine-tune the timing for ${name.trim()}. You can change it later.`}
	width={step === 2 && !atLimit ? 640 : 480}
>
	{#if atLimit}
		<div class="limit">
			<span class="limit-icon"><Icon name="groups" size={22} /></span>
			<h3>You've made {FREE_GROUP_LIMIT} groups</h3>
			<p>
				Free accounts can create {FREE_GROUP_LIMIT}. Upgrade to Pro for unlimited groups, or delete one you no longer need. You can still join groups other
				people make.
			</p>
			<Button variant="accent" size="lg" block icon="sparkle" onclick={upgrade}>See Pro</Button>
		</div>
	{:else if step === 1}
		<form class="form" onsubmit={next} novalidate>
			<StepDots total={2} step={1} />
			<div class="name-row">
				<GroupTile {monogram} {color} size={50} />
				<TextField label="Group name" bind:value={name} placeholder="e.g. Lake District crew" maxlength={80} {error} autofocus />
			</div>

			<div class="colors" role="radiogroup" aria-label="Tile colour">
				{#each TILE_COLORS as c (c)}
					<button
						type="button"
						role="radio"
						aria-checked={color === c}
						aria-label="Colour {c.slice(1)}"
						class:on={color === c}
						style:background="var(--{c})"
						onclick={() => (color = c)}
					></button>
				{/each}
			</div>

			{#if templates.length}
				<fieldset>
					<legend class="eyebrow">Your templates</legend>
					<div class="chips">
						{#each templates as t (t.id)}
							<Chip selected={templateId === t.id} onclick={() => pickTemplate(t)}>{t.name}</Chip>
						{/each}
					</div>
				</fieldset>
			{/if}

			<fieldset>
				<legend class="eyebrow">{templates.length ? 'Or start from' : 'What are you planning?'}</legend>
				<div class="chips">
					{#each PRESETS.filter((p) => QUICK.includes(p.id)) as p (p.id)}
						<Chip selected={!templateId && preset === p.id} onclick={() => pickPreset(p.id)}>{p.label}</Chip>
					{/each}
				</div>
				<p class="summary">{summarize(requirement)}</p>
			</fieldset>

			<Button type="submit" variant="accent" size="lg" block icon="arrow-right">Next: timing</Button>
		</form>
	{:else}
		<div class="form">
			<StepDots total={2} step={2} />
			<RequirementsEditor bind:requirement country={user.country} />
			<SaveTemplate {requirement} onsaved={(t) => (templates = [...templates, t])} />
		</div>
	{/if}

	{#snippet footer()}
		{#if !atLimit && step === 2}
			<div class="bar">
				<FitPreview {requirement} timeZone={user.timezone} />
				<div class="bar-actions">
					<Button variant="secondary" icon="chevron-left" onclick={() => (step = 1)}>Back</Button>
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
	.colors {
		display: flex;
		gap: 10px;
	}
	.colors button {
		width: 44px;
		height: 44px;
		border-radius: 14px;
		border: 1px solid var(--glass-border);
		transition: transform 0.15s ease;
	}
	.colors button:hover {
		transform: translateY(-2px);
	}
	.colors button.on {
		outline: 2.5px solid var(--ink);
		outline-offset: 3px;
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
	.summary {
		font-size: 13px;
		color: var(--muted);
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
