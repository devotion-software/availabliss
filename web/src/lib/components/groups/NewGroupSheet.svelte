<script lang="ts">
	import { goto, invalidate } from '$app/navigation';
	import { TILE_COLORS, type TileColor } from '#lib/types';
	import { createGroupSchema } from '#lib/schemas';
	import { createGroup } from '#lib/client/groups';
	import { ApiError } from '#lib/client/api';
	import { getPlan } from '#lib/client/plan';
	import { upgradeSheet } from '#lib/client/ui-state.svelte';
	import { FREE_GROUP_LIMIT } from '#lib/plans';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { toasts } from '#lib/client/toast.svelte';
	import { useFormat } from '#lib/client/format';
	import { defaultRequirement, summarize, PRESETS, type PresetId } from '#lib/client/requirements';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Chip from '#lib/components/ui/Chip.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';

	interface Props {
		open: boolean;
		timeZone: string;
	}
	let { open = $bindable(), timeZone }: Props = $props();

	const fmt = useFormat();
	const QUICK: PresetId[] = ['2h', 'evening', 'day', 'weekend', 'week'];

	let name = $state('');
	let color = $state<TileColor>('t1');
	let preset = $state<PresetId>('weekend');
	let error = $state<string | null>(null);
	let busy = $state(false);
	/** Free accounts at their group limit see an upgrade prompt instead of the form. */
	let atLimit = $state(false);

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

	function upgrade() {
		open = false;
		upgradeSheet.showFor(`You've used your ${FREE_GROUP_LIMIT} free groups. Pro gives you as many as you like.`);
	}

	const requirement = $derived(defaultRequirement(timeZone, fmt.current.today(), preset));
	const monogram = $derived(
		name
			.trim()
			.split(/\s+/)
			.filter(Boolean)
			.slice(0, 2)
			.map((w) => w[0]?.toUpperCase())
			.join('') || '?'
	);

	function reset() {
		name = '';
		color = TILE_COLORS[Math.floor(Math.random() * TILE_COLORS.length)];
		preset = 'weekend';
		error = null;
	}

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		const parsed = createGroupSchema.safeParse({ name, color, requirement });
		if (!parsed.success) {
			error = parsed.error.issues[0]?.message ?? 'Give your group a name';
			return;
		}
		busy = true;
		error = null;
		try {
			const group = await createGroup({ name: parsed.data.name, color, requirement });
			open = false;
			reset();
			await invalidate('app:groups');
			await goto(`/app/groups/${group.id}`);
		} catch (err) {
			if (err instanceof ApiError && err.code === 'upgrade_required') atLimit = true;
			else if (err instanceof ApiError && err.issue('name')) error = err.issue('name')!;
			else toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<Sheet bind:open title="New group" description="Name it, pick what you're planning. You can fine-tune timing later.">
	{#if atLimit}
		<div class="limit">
			<span class="limit-icon"><Icon name="groups" size={22} /></span>
			<h3>You've made {FREE_GROUP_LIMIT} groups</h3>
			<p>Free accounts can create {FREE_GROUP_LIMIT}. Upgrade to Pro for unlimited groups, or delete one you no longer need. You can still join groups other people make.</p>
			<Button variant="accent" size="lg" block icon="sparkle" onclick={upgrade}>See Pro</Button>
		</div>
	{:else}
	<form class="form" onsubmit={submit} novalidate>
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

		<fieldset>
			<legend class="eyebrow">How long?</legend>
			<div class="chips">
				{#each PRESETS.filter((p) => QUICK.includes(p.id)) as p (p.id)}
					<Chip selected={preset === p.id} onclick={() => (preset = p.id)}>{p.label}</Chip>
				{/each}
			</div>
			<p class="summary">{summarize(requirement)}</p>
		</fieldset>

		<Button type="submit" variant="accent" size="lg" block loading={busy}>Create group</Button>
	</form>
	{/if}
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
