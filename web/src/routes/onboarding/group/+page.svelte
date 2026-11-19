<script lang="ts">
	import { untrack } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { TILE_COLORS, type TileColor } from '#lib/types';
	import { createGroup } from '#lib/client/groups';
	import { updateMe } from '#lib/client/me';
	import { browserTimeZone, todayIn } from '#lib/client/dates';
	import { defaultRequirement } from '#lib/client/requirements';
	import { requirementSchema } from '#lib/schemas';
	import { toasts } from '#lib/client/toast.svelte';
	import { monogram } from '#lib/monogram';
	import Button from '#lib/components/ui/Button.svelte';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import StepDots from '#lib/components/onboarding/StepDots.svelte';
	import RequirementsEditor from '#lib/components/timing/RequirementsEditor.svelte';
	import FitPreview from '#lib/components/timing/FitPreview.svelte';

	/** Step 3 of the sign-up wizard: your first group. Skippable. */
	let { data } = $props();

	let name = $state('');
	/** The full timing sentence, as in New group, with a live count of how many times fit. */
	const tz = untrack(() => data.user.timezone) || browserTimeZone();
	let requirement = $state(defaultRequirement(tz, todayIn(tz), 'weekend'));
	const color: TileColor = TILE_COLORS[Math.floor(Math.random() * TILE_COLORS.length)];
	let busy = $state<'create' | 'skip' | null>(null);

	async function create(e: SubmitEvent) {
		e.preventDefault();
		if (!name.trim()) return;
		const timing = requirementSchema.safeParse({ ...$state.snapshot(requirement), timezone: requirement.timezone || tz });
		if (!timing.success) {
			toasts.error(timing.error.issues[0]?.message ?? 'Check the timing');
			return;
		}
		busy = 'create';
		try {
			const group = await createGroup({ name: name.trim(), color, requirement: timing.data });
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
	<p>Name the event you’re planning. You can change everything later.</p>
</div>

<form class="form" onsubmit={create} novalidate>
	<div class="name-row">
		<GroupTile monogram={monogram(name.trim() || '?')} {color} size={52} />
		<TextField label="Group name" placeholder="e.g. Lake District weekend" bind:value={name} maxlength={80} autofocus />
	</div>
	<fieldset>
		<legend class="eyebrow">What are you planning?</legend>
		<RequirementsEditor bind:requirement country={data.user.country} />
	</fieldset>
	<span class="spacer"></span>
	<FitPreview {requirement} timeZone={tz} />
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
