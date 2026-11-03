<script lang="ts">
	import type { Member, Proposal } from '#lib/types';
	import { getProposal } from '#lib/client/proposals';
	import { live, proposalOf } from '#lib/client/live.svelte';
	import { useFormat, monthShort } from '#lib/client/format';
	import { memberLabel } from '#lib/client/members';
	import { clashShort } from '#lib/components/proposals/clash';

	/** A proposal posted in the chat: date block, who proposed, how many accepted, link to reply. */
	interface Props {
		proposalId: string;
		members: Map<string, Member>;
		meId: string;
		compact?: boolean;
	}
	let { proposalId, members, meId, compact = false }: Props = $props();

	const fmt = useFormat();
	let proposal = $state<Proposal | null>(null);
	let failed = $state(false);

	$effect(() => {
		const id = proposalId;
		getProposal(id)
			.then((p) => (proposal = p))
			.catch(() => (failed = true));
		const off = live.on(['proposal.created', 'proposal.updated', 'proposal.confirmed'], (e) => {
			const p = proposalOf(e);
			if (p.id === id) proposal = p;
		});
		const offReconnect = live.onReconnect(() => {
			getProposal(id)
				.then((p) => (proposal = p))
				.catch(() => {});
		});
		return () => {
			off();
			offReconnect();
		};
	});

	const startKey = $derived(proposal ? fmt.current.dayKey(proposal.start) : null);
	const accepted = $derived(proposal?.responses.filter((r) => r.response === 'accepted').length ?? 0);
	const total = $derived(proposal?.responses.length || members.size);
	const myResponse = $derived(proposal?.responses.find((r) => r.userId === meId)?.response ?? 'pending');
	const clash = $derived(proposal?.status === 'open' ? clashShort(proposal.busyMemberIds ?? [], members) : null);
	const status = $derived.by(() => {
		if (!proposal) return { text: '', tone: 'muted' };
		if (proposal.status === 'confirmed') return { text: "Everyone's in", tone: 'ok' };
		if (proposal.status === 'cancelled') return { text: 'Withdrawn', tone: 'muted' };
		return { text: `${accepted} of ${total} accepted`, tone: 'ok' };
	});
</script>

{#if proposal && startKey}
	<a class="card glass" class:compact href="/app/proposals/{proposal.id}" class:cancelled={proposal.status === 'cancelled'}>
		<span class="date" aria-hidden="true">
			<span class="mon">{monthShort(startKey).toUpperCase()}</span>
			<span class="num">{Number(startKey.slice(8))}</span>
		</span>
		<span class="text">
			<span class="who">{memberLabel(members, proposal.createdBy, meId, { first: true })} proposed {proposal.title ? proposal.title : 'a time'}</span>
			<span class="when">{fmt.current.when(proposal.start, proposal.end, proposal.allDay)}</span>
			<span class="status {status.tone}">{status.text}{#if clash}<span class="clash"> · {clash}</span>{/if}</span>
		</span>
		<span class="cta">{proposal.status === 'open' && myResponse === 'pending' ? 'Reply' : 'View'}</span>
	</a>
{:else if failed}
	<p class="missing">A proposal that's no longer available</p>
{:else}
	<div class="card glass skeleton" aria-hidden="true"></div>
{/if}

<style>
	.card {
		align-self: stretch;
		border-radius: 20px;
		padding: 12px;
		display: flex;
		gap: 12px;
		align-items: center;
		text-decoration: none;
		color: var(--ink);
		transition: transform 0.15s ease;
	}
	.card:hover {
		color: var(--ink);
		transform: translateY(-1px);
	}
	.compact {
		background: var(--surface);
		box-shadow: none;
		backdrop-filter: none;
		-webkit-backdrop-filter: none;
		border-radius: 16px;
		padding: 10px;
	}
	.cancelled {
		opacity: 0.6;
	}
	.skeleton {
		height: 74px;
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
	.mon {
		font-size: 10px;
		font-weight: 600;
	}
	.num {
		font-family: var(--font-display);
		font-size: 19px;
		font-weight: 700;
		letter-spacing: -0.025em;
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.who {
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
	.status {
		font-size: 12px;
		font-weight: 600;
	}
	.status.ok {
		color: var(--ok-ink);
	}
	.status.muted {
		color: var(--muted);
	}
	.cta {
		font-size: 13px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	.missing {
		align-self: center;
		font-size: 12px;
		color: var(--muted);
	}
	.clash {
		color: var(--warn-ink);
	}
</style>
