<script lang="ts">
	import type { Group, Proposal } from '#lib/types';
	import { useFormat, monthShort, dayNumber } from '#lib/client/format';
	import { memberMap, memberLabel } from '#lib/client/members';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { RESPONSE_PILL, acceptedCount, askedCount, myResponse } from './responses';
	import { clashShort } from './clash';

	/** A proposal in a list: date badge, who proposed, range, acceptance, your status. */
	interface Props {
		proposal: Proposal;
		group: Group;
		meId: string;
	}
	let { proposal, group, meId }: Props = $props();

	const fmt = useFormat();
	const members = $derived(memberMap(group));
	const day = $derived(fmt.current.dayKey(proposal.start));
	const mine = $derived(myResponse(proposal, meId));
	const yes = $derived(acceptedCount(proposal));
	const total = $derived(askedCount(proposal, group));
	const clash = $derived(proposal.status === 'open' ? clashShort(proposal.busyMemberIds ?? [], members) : null);
	const by = $derived(proposal.createdBy === meId ? 'You' : memberLabel(members, proposal.createdBy, meId, { first: true }));
</script>

<a class="row glass" href="/app/proposals/{proposal.id}" class:muted={proposal.status !== 'open'}>
	<span class="date" class:confirmed={proposal.status === 'confirmed'} aria-hidden="true">
		<span class="mon">{monthShort(day).toUpperCase()}</span>
		<span class="num">{dayNumber(day)}</span>
	</span>
	<span class="text">
		<span class="by">{by} proposed{proposal.title ? ` · ${proposal.title}` : ''}</span>
		<span class="when">{fmt.current.when(proposal.start, proposal.end, proposal.allDay)}</span>
		{#if proposal.status === 'open'}
			<span class="count">{yes} of {total} accepted{#if clash}<span class="clash"> · {clash}</span>{/if}</span>
		{:else if proposal.status === 'confirmed'}
			<span class="count">Everyone's in</span>
		{:else}
			<span class="count off">Withdrawn</span>
		{/if}
	</span>
	{#if proposal.status === 'open' && mine}
		<StatusPill tone={RESPONSE_PILL[mine].tone}>{mine === 'pending' ? 'Reply' : RESPONSE_PILL[mine].text}</StatusPill>
	{:else if proposal.status === 'confirmed'}
		<StatusPill tone="mint" icon="check">Confirmed</StatusPill>
	{/if}
	<Icon name="chevron-right" size={18} class="chev" />
</a>

<style>
	.row {
		border-radius: 20px;
		padding: 12px;
		display: flex;
		gap: 12px;
		align-items: center;
		text-decoration: none;
		color: var(--ink);
		transition: transform 0.2s ease;
	}
	.row:hover {
		color: var(--ink);
		transform: translateY(-1px);
	}
	.row.muted {
		opacity: 0.8;
	}
	.date {
		width: 46px;
		height: 50px;
		flex: none;
		border-radius: 12px;
		background: var(--accent);
		color: var(--on-accent);
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		line-height: 1;
	}
	.date.confirmed {
		background: var(--ok);
	}
	.muted .date:not(.confirmed) {
		background: var(--line-2);
		color: var(--ink);
	}
	.mon {
		font-size: 10px;
		font-weight: 600;
	}
	.num {
		font-family: var(--font-display);
		font-size: 19px;
		font-weight: 700;
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.by {
		font-size: 12px;
		color: var(--muted);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.when {
		font-size: 15px;
		font-weight: 600;
	}
	.count {
		font-size: 12px;
		font-weight: 600;
		color: var(--ok-ink);
	}
	.count.off {
		color: var(--muted);
	}
	.row :global(.chev) {
		color: var(--muted);
	}
	.clash {
		color: var(--warn-ink);
	}
</style>
