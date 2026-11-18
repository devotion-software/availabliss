<script lang="ts">
	import { onMount } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import { myPlansIcsUrl } from '#lib/client/plans';
	import { rememberAddedToCalendar, wasAddedToCalendar } from '#lib/client/calendar-links';
	import { plural } from '#lib/client/format';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import PlanRow from '#lib/components/plans/PlanRow.svelte';

	let { data } = $props();
	const plans = $derived(data.plans);

	/** Plans this browser added to a calendar; read after mount, since storage is browser-only. */
	const added = new SvelteSet<string>();
	onMount(() => {
		for (const p of [...plans.going, ...plans.notGoing, ...plans.past]) if (wasAddedToCalendar(p.id)) added.add(p.id);
	});

	/** The .ics download has every plan in Coming up, so they all count as added. */
	function addedAll() {
		for (const p of plans.going) {
			rememberAddedToCalendar(p.id);
			added.add(p.id);
		}
	}
</script>

<svelte:head><title>My plans · Availabliss</title></svelte:head>

<div class="plans">
	<PageHeader title="My plans" subtitle="Everything you're going to, across your groups" backHref="/app/activity" backLabel="Back to Activity" />

	<section aria-labelledby="coming-up">
		<div class="section-head">
			<h2 class="eyebrow" id="coming-up">Coming up{plans.going.length ? ` · ${plans.going.length}` : ''}</h2>
			{#if plans.going.length}
				<Button href={myPlansIcsUrl()} download size="sm" variant="secondary" icon="calendar-plus" onclick={addedAll}>Add all to calendar</Button>
			{/if}
		</div>
		{#if plans.going.length}
			<ul class="list glass">
				{#each plans.going as plan (plan.id)}
					<li><PlanRow {plan} inCalendar={added.has(plan.id)} /></li>
				{/each}
			</ul>
		{:else}
			<div class="glass empty">
				<EmptyState icon="calendar" title="Nothing planned yet" message="When a group settles on a time, it shows up here." compact />
			</div>
		{/if}
	</section>

	{#if plans.notGoing.length}
		<section aria-labelledby="cant-make-it" class="quiet">
			<h2 class="eyebrow" id="cant-make-it">Can't make it</h2>
			<p class="hint">{plural(plans.notGoing.length, 'plan')} you said no to. Open one to change your reply.</p>
			<ul class="list glass">
				{#each plans.notGoing as plan (plan.id)}
					<li><PlanRow {plan} inCalendar={added.has(plan.id)} quiet /></li>
				{/each}
			</ul>
		</section>
	{/if}

	<section aria-labelledby="past">
		<h2 class="eyebrow" id="past">Past</h2>
		{#if plans.past.length}
			<ul class="list glass">
				{#each plans.past as plan (plan.id)}
					<li><PlanRow {plan} inCalendar={added.has(plan.id)} quiet /></li>
				{/each}
			</ul>
		{:else}
			<div class="glass empty">
				<EmptyState icon="clock" title="No past plans" message="Plans from the last year show up here once they've happened." compact />
			</div>
		{/if}
	</section>
</div>

<style>
	.plans {
		display: flex;
		flex-direction: column;
		gap: 20px;
		max-width: 720px;
		width: 100%;
		margin: 0 auto;
	}
	section {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.section-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		min-height: 36px;
	}
	.section-head .eyebrow {
		margin: 0;
	}
	.list {
		list-style: none;
		margin: 0;
		padding: 4px 8px;
		border-radius: 24px;
	}
	li + li {
		border-top: 1px solid var(--line);
	}
	.quiet .list {
		opacity: 0.85;
	}
	.hint {
		margin: -4px 0 0;
		font-size: 13px;
		color: var(--muted);
	}
	.empty {
		border-radius: 28px;
	}
</style>
