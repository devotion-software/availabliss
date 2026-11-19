<script lang="ts">
	import { invalidate } from '$app/navigation';
	import { live } from '#lib/client/live.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import ProposeSheet from '#lib/components/events/ProposeSheet.svelte';
	import ProposalRow from '#lib/components/proposals/ProposalRow.svelte';
	import EventEditSheet from '#lib/components/events/EventEditSheet.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { useFormat } from '#lib/client/format';
	import type { GroupEvent } from '#lib/types';

	let { data } = $props();

	const byStart = (a: { start: string }, b: { start: string }) => a.start.localeCompare(b.start);
	const open = $derived(data.proposals.filter((p) => p.status === 'open').sort(byStart));
	const confirmed = $derived(data.proposals.filter((p) => p.status === 'confirmed').sort(byStart));
	const withdrawn = $derived(
		data.proposals
			.filter((p) => p.status === 'cancelled')
			.sort(byStart)
			.reverse()
	);

	let proposing = $state(false);
	let editing = $state<GroupEvent | null>(null);
	const fmt = useFormat();
	const isAdmin = $derived(data.group.role === 'owner' || data.group.role === 'admin');
	const canEdit = (e: GroupEvent) => e.createdBy === data.user.id || isAdmin;

	$effect(() => {
		const id = data.group.id;
		const off = live.on(['proposal.created', 'proposal.updated', 'proposal.confirmed', 'event.changed'], (e) => {
			if (e.groupId === id) invalidate('app:proposals');
		});
		const offReconnect = live.onReconnect(() => invalidate('app:proposals'));
		return () => {
			off();
			offReconnect();
		};
	});
</script>

<svelte:head><title>{data.group.name} Plans · Availabliss</title></svelte:head>

<div class="page">
	<PageHeader
		title="{data.group.name} Plans"
		backHref="/app/groups/{data.group.id}"
		backLabel="Back to calendar"
		backOnPhoneOnly
		crumbs={[{ label: 'Groups', href: '/app' }, { label: data.group.name, href: `/app/groups/${data.group.id}` }, { label: 'Plans' }]}
	>
		{#snippet actions()}
			<Button variant="secondary" size="sm" icon="calendar-plus" href="/app/groups/{data.group.id}/events/new">Add event</Button>
			<Button variant="accent" size="sm" icon="plus" onclick={() => (proposing = true)}>Propose</Button>
		{/snippet}
	</PageHeader>

	<!-- Events coming up (added straight to the calendar, or confirmed plans): their creator or an admin can edit or delete them. -->
	{#if data.events.length}
		<section class="section" aria-labelledby="sec-events">
			<h2 id="sec-events" class="eyebrow">Coming up</h2>
			<div class="list">
				{#each data.events as event (event.id)}
					<div class="event glass">
						{#if event.image}<img class="cover" src={event.image} alt="" />{/if}
						<div class="row">
							<span class="icon"><Icon name="calendar" size={18} /></span>
							<span class="text">
								<span class="title">{event.title}</span>
								<span class="meta">{[fmt.current.when(event.start, event.end, event.allDay), event.location].filter(Boolean).join(' · ')}</span>
							</span>
							{#if canEdit(event)}
								<Button variant="ghost" size="sm" icon="pencil" onclick={() => (editing = event)} aria-label="Edit {event.title}">Edit</Button>
							{/if}
						</div>
					</div>
				{/each}
			</div>
		</section>
	{/if}

	{#if data.proposals.length === 0 && data.events.length === 0}
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
{#key editing?.id}
	<EventEditSheet event={editing} onclose={() => (editing = null)} onchanged={() => invalidate('app:proposals')} />
{/key}

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
	.event {
		border-radius: 22px;
		overflow: hidden;
	}
	.cover {
		display: block;
		width: 100%;
		height: 120px;
		object-fit: cover;
	}
	.row {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 14px;
	}
	.icon {
		width: 38px;
		height: 38px;
		flex: none;
		border-radius: 12px;
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.title {
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.meta {
		font-size: 13px;
		color: var(--muted);
	}
</style>
