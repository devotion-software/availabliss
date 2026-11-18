<script lang="ts">
	import type { Group, Proposal, Slot } from '#lib/types';
	import { getNextSuggestion, RHYTHM_LABELS, type NextSuggestion } from '#lib/client/groups';
	import { listProposals } from '#lib/client/proposals';
	import { formatDayKey } from '#lib/client/format';
	import { live } from '#lib/client/live.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import Button from '#lib/components/ui/Button.svelte';

	/**
	 * "Next one": for a group that meets regularly, the free time nearest the next meet-up,
	 * with Propose. Hidden without a rhythm, or while a plan is already coming up or proposed.
	 */
	interface Props {
		group: Group;
		onpropose: (slot: Slot | { day: string }) => void;
	}
	let { group, onpropose }: Props = $props();

	let suggestion = $state<NextSuggestion | null>(null);
	let proposals = $state<Proposal[] | null>(null);

	// Something already planned or asked about, still ahead: no need to suggest the next one yet.
	const planned = $derived.by(() => {
		if (!proposals) return true;
		const now = Date.now();
		return proposals.some((p) => (p.status === 'open' || p.status === 'confirmed') && new Date(p.end).getTime() > now);
	});

	const rhythm = $derived(group.rhythm);
	$effect(() => {
		const id = group.id;
		void rhythm?.every;
		if (!rhythm) {
			suggestion = null;
			return;
		}
		let request = 0;
		const refresh = () => {
			const n = ++request;
			Promise.all([getNextSuggestion(id), listProposals(id)])
				.then(([s, list]) => {
					if (n !== request) return;
					suggestion = s;
					proposals = list;
				})
				.catch(() => {});
		};
		refresh();
		const off = live.on(
			['availability.changed', 'proposal.created', 'proposal.updated', 'proposal.confirmed', 'event.changed'],
			(e) => {
				if (e.groupId === null || e.groupId === id) refresh();
			}
		);
		const offReconnect = live.onReconnect(refresh);
		return () => {
			request++;
			off();
			offReconnect();
		};
	});

	const slot = $derived(suggestion?.slot ?? null);
	const everyone = $derived(!slot?.missingMemberIds?.length);
</script>

{#if rhythm && suggestion && !planned}
	<section class="next glass" aria-labelledby="next-{group.id}">
		<div class="top">
			<h2 id="next-{group.id}" class="eyebrow">Next one</h2>
			<span class="rhythm"><Icon name="repeat" size={13} /> {RHYTHM_LABELS[rhythm.every]}</span>
		</div>
		<div class="row">
			<p>
				{#if slot}
					<strong>{slot.label}</strong>
					<span>{everyone ? 'works for everyone' : 'works for most of the group'}</span>
				{:else}
					<strong>Nothing near {formatDayKey(suggestion.target)} works yet</strong>
					<span>You can still propose it and see who can make it.</span>
				{/if}
			</p>
			<Button
				variant={slot ? 'accent' : 'secondary'}
				size="sm"
				onclick={() => onpropose(slot ?? { day: suggestion!.target })}>Propose</Button
			>
		</div>
	</section>
{/if}

<style>
	.next {
		border-radius: 22px;
		padding: 12px 14px;
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
	}
	.rhythm {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		font-size: 12px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	.row {
		display: flex;
		align-items: center;
		gap: 12px;
	}
	p {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	strong {
		font-size: 15px;
		font-weight: 650;
	}
	p span {
		font-size: 12px;
		color: var(--muted);
	}
</style>
