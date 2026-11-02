<script lang="ts">
	import { invalidate } from '$app/navigation';
	import { live } from '#lib/client/live.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import ProposeSheet from '#lib/components/events/ProposeSheet.svelte';
	import ProposalRow from '#lib/components/proposals/ProposalRow.svelte';

	let { data } = $props();

	const byStart = (a: { start: string }, b: { start: string }) => a.start.localeCompare(b.start);
	const open = $derived(data.proposals.filter((p) => p.status === 'open').sort(byStart));
	const confirmed = $derived(data.proposals.filter((p) => p.status === 'confirmed').sort(byStart));
	const withdrawn = $derived(data.proposals.filter((p) => p.status === 'cancelled').sort(byStart).reverse());

	let proposing = $state(false);

	$effect(() => {
		const id = data.group.id;
		return live.on(['proposal.created', 'proposal.updated', 'proposal.confirmed'], (e) => {
			if (e.groupId === id) invalidate('app:proposals');
		});
	});
</script>

<svelte:head><title>Proposals · {data.group.name} · Availabliss</title></svelte:head>

<div class="page">
	<PageHeader title="Proposals" subtitle={data.group.name} backHref="/app/groups/{data.group.id}" backLabel="Back to calendar">
		{#snippet actions()}
			<Button variant="accent" size="sm" icon="plus" onclick={() => (proposing = true)}>Propose</Button>
		{/snippet}
	</PageHeader>

	{#if data.proposals.length === 0}
		<div class="glass empty">
			<EmptyState
				icon="check-circle"
				title="Nothing proposed yet"
				message="Pick a time that works from the calendar, or suggest one yourself. Everyone gets asked to accept."
			>
				{#snippet actions()}
					<Button variant="solid" size="sm" href="/app/groups/{data.group.id}">See what works</Button>
					<Button variant="secondary" size="sm" onclick={() => (proposing = true)}>Propose a time</Button>
				{/snippet}
			</EmptyState>
		</div>
	{/if}

	{#each [{ title: 'Waiting on replies', items: open }, { title: 'Confirmed', items: confirmed }, { title: 'Withdrawn', items: withdrawn }] as section (section.title)}
		{#if section.items.length}
			<section class="section" aria-labelledby="sec-{section.title}">
				<h2 id="sec-{section.title}" class="eyebrow">{section.title}</h2>
				<div class="list">
					{#each section.items as proposal (proposal.id)}
						<ProposalRow {proposal} group={data.group} meId={data.user.id} />
					{/each}
				</div>
			</section>
		{/if}
	{/each}
</div>

<ProposeSheet bind:open={proposing} group={data.group} meId={data.user.id} />

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: 18px;
		max-width: 720px;
		width: 100%;
	}
	.empty {
		border-radius: 28px;
	}
	.section {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.eyebrow {
		padding-left: 4px;
	}
	.list {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
</style>
