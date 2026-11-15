<script lang="ts">
	import type { Group, Proposal } from '#lib/types';
	import { listProposals } from '#lib/client/proposals';
	import { live } from '#lib/client/live.svelte';
	import ProposalRow from './ProposalRow.svelte';

	/**
	 * The calendar's "Plans" card (phones): open proposals first, then the next confirmed
	 * plan, two at most. Renders nothing when there's neither.
	 */
	interface Props {
		group: Group;
		meId: string;
	}
	let { group, meId }: Props = $props();

	let proposals = $state<Proposal[]>([]);

	const byStart = (a: Proposal, b: Proposal) => a.start.localeCompare(b.start);
	const items = $derived.by(() => {
		const now = Date.now();
		const ahead = proposals.filter((p) => new Date(p.end).getTime() > now).sort(byStart);
		const open = ahead.filter((p) => p.status === 'open');
		const next = ahead.find((p) => p.status === 'confirmed');
		return [...open, ...(next ? [next] : [])].slice(0, 2);
	});

	// Loaded here rather than in the calendar's load: refreshing it there would rerun that
	// load and snap the calendar back to this month. Kept fresh like the Plans screen.
	$effect(() => {
		const id = group.id;
		let request = 0;
		const refresh = () => {
			const n = ++request;
			listProposals(id)
				.then((list) => n === request && (proposals = list))
				.catch(() => {});
		};
		refresh();
		const off = live.on(['proposal.created', 'proposal.updated', 'proposal.confirmed'], (e) => {
			if (e.groupId === id) refresh();
		});
		const offReconnect = live.onReconnect(refresh);
		return () => {
			request++;
			off();
			offReconnect();
		};
	});
</script>

{#if items.length}
	<section class="plans glass" aria-labelledby="plans-heading">
		<div class="top">
			<h2 id="plans-heading" class="eyebrow">Plans</h2>
			<a href="/app/groups/{group.id}/proposals">See all</a>
		</div>
		{#each items as proposal (proposal.id)}
			<ProposalRow {proposal} {group} {meId} compact />
		{/each}
	</section>
{/if}

<style>
	.plans {
		border-radius: 22px;
		padding: 10px 6px 6px;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0 8px 4px 10px;
	}
	a {
		font-size: 13px;
		font-weight: 600;
		text-decoration: none;
		min-height: 32px;
		display: inline-flex;
		align-items: center;
	}
</style>
