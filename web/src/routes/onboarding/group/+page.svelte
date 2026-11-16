<script lang="ts">
	import { untrack } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { TILE_COLORS, type TileColor } from '#lib/types';
	import { createGroup } from '#lib/client/groups';
	import { updateMe } from '#lib/client/me';
	import { browserTimeZone, todayIn } from '#lib/client/dates';
	import { applyPreset, defaultRequirement, PRESETS, type PresetId } from '#lib/client/requirements';
	import { toasts } from '#lib/client/toast.svelte';
	import { monogram } from '#lib/monogram';
	import Button from '#lib/components/ui/Button.svelte';
	import Chip from '#lib/components/ui/Chip.svelte';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import StepDots from '#lib/components/onboarding/StepDots.svelte';
	import CustomLength from '#lib/components/timing/CustomLength.svelte';

	/** Step 3 of the sign-up wizard: your first group. Skippable. */
	let { data } = $props();

	let name = $state('');
	let preset = $state<PresetId>('weekend');
	/** Only the length is chosen here; the rest of the timing keeps its defaults until later. */
	const tz = untrack(() => data.user.timezone) || browserTimeZone();
	let requirement = $state(defaultRequirement(tz, todayIn(tz), 'weekend'));
	/** "More options": every preset, plus a custom length. */
	let more = $state(false);
	const color: TileColor = TILE_COLORS[Math.floor(Math.random() * TILE_COLORS.length)];
	let busy = $state<'create' | 'skip' | null>(null);
	const COMMON: PresetId[] = ['2h', 'evening', 'day', 'weekend', 'week'];
	const choices = $derived(more ? PRESETS : PRESETS.filter((p) => COMMON.includes(p.id)));

	function pick(id: PresetId) {
		preset = id;
		if (id === 'custom') {
			// Start custom at hours rather than a span that looks like the weekend.
			if (requirement.length.type === 'span') requirement = { ...requirement, length: { type: 'duration', minutes: 180 }, window: { from: '08:00', to: '23:00' } };
		} else requirement = applyPreset(requirement, id);
	}

	async function create(e: SubmitEvent) {
		e.preventDefault();
		if (!name.trim()) return;
		busy = 'create';
		try {
			const group = await createGroup({ name: name.trim(), color, requirement: $state.snapshot(requirement) });
			await goto(`/onboarding/invite?group=${group.id}`);
		} catch (err) {
			toasts.error(err);
			busy = null;
		}
	}

	async function skip() {
		busy = 'skip';
		try {
			await updateMe({ onboarded: true });
			await goto(page.url.searchParams.get('then') === 'my-time' ? '/app/my-time' : '/app', { invalidateAll: true });
		} catch (err) {
			toasts.error(err);
			busy = null;
		}
	}
</script>

<svelte:head><title>Your first group · Availabliss</title></svelte:head>

<div class="top">
	<IconButton href="/onboarding/calendars" icon="chevron-left" label="Back" />
	<StepDots step={3} total={data.steps} />
</div>

<div class="intro">
	<h1>Start your first group</h1>
	<p>Who are you trying to find a time with? You can change everything later.</p>
</div>

<form class="form" onsubmit={create} novalidate>
	<div class="name-row">
		<GroupTile monogram={monogram(name.trim() || '?')} {color} size={52} />
		<TextField label="Group name" placeholder="e.g. Lake District crew" bind:value={name} maxlength={80} autofocus />
	</div>
	<fieldset>
		<legend class="eyebrow">What are you planning?</legend>
		<div class="chips">
			{#each choices as c (c.id)}
				<Chip selected={preset === c.id} onclick={() => pick(c.id)}>{c.label}</Chip>
			{/each}
			{#if !more}
				<Chip selected={false} onclick={() => (more = true)}>More options</Chip>
			{/if}
		</div>
		{#if preset === 'custom'}
			<div class="custom"><CustomLength bind:requirement /></div>
		{/if}
	</fieldset>
	<span class="spacer"></span>
	<Button type="submit" size="lg" block loading={busy === 'create'} disabled={!name.trim() || !!busy}>Create group</Button>
	<button type="button" class="skip" disabled={!!busy} onclick={skip}>Skip for now</button>
</form>

<style>
	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.intro {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	h1 {
		font-size: 30px;
		font-weight: 700;
		line-height: 1.05;
	}
	.intro p {
		font-size: 14px;
		color: var(--muted);
	}
	.form {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.name-row {
		display: flex;
		align-items: flex-end;
		gap: 12px;
	}
	.name-row > :global(.field) {
		flex: 1;
	}
	fieldset {
		border: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}
	.custom {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.spacer {
		flex: 1;
	}
	.skip {
		margin-top: -6px;
		min-height: 44px;
		font-size: 14px;
		font-weight: 600;
		color: var(--ink);
	}
</style>
