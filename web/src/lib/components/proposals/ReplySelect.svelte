<script lang="ts">
	import type { Proposal, ResponseKind } from '#lib/types';
	import { respondToProposal, type Reply } from '#lib/client/proposals';
	import { toasts } from '#lib/client/toast.svelte';

	/**
	 * "Your reply: Can make it ⌄" under a proposal (or a confirmed plan) on the Plans page, to change
	 * it without opening it. Only for people who were asked (members who joined since weren't).
	 */
	interface Props {
		proposal: Proposal;
		meId: string;
		/** The updated proposal, once saved. */
		onchanged?: (proposal: Proposal) => void;
	}
	let { proposal, meId, onchanged }: Props = $props();

	const LABEL: Record<ResponseKind, string> = { accepted: 'Can make it', maybe: 'Maybe', declined: 'Can’t make it', pending: 'Not replied' };
	const mine = $derived(proposal.responses.find((r) => r.userId === meId)?.response ?? null);
	let busy = $state(false);

	async function change(e: Event) {
		const select = e.currentTarget as HTMLSelectElement;
		const next = select.value as Reply;
		busy = true;
		try {
			onchanged?.(await respondToProposal(proposal.id, next));
		} catch (err) {
			select.value = mine ?? 'pending';
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

{#if mine}
	<label class="reply" class:waiting={mine === 'pending'}>
		<span>Your reply:</span>
		<select value={mine} disabled={busy} onchange={change} aria-label="Your reply to {proposal.title ?? 'this proposal'}">
			{#if mine === 'pending'}<option value="pending" disabled>{LABEL.pending}</option>{/if}
			<option value="accepted">{LABEL.accepted}</option>
			<option value="maybe">{LABEL.maybe}</option>
			<option value="declined">{LABEL.declined}</option>
		</select>
	</label>
{/if}

<style>
	.reply {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 0 12px;
		min-height: 36px;
		font-size: 13px;
		font-weight: 600;
		color: var(--muted);
	}
	.reply.waiting {
		color: var(--accent-ink);
	}
	select {
		font: inherit;
		color: inherit;
		background: transparent;
		border: 0;
		padding: 4px 2px;
		cursor: pointer;
	}
	select:focus-visible {
		outline: 2px solid var(--accent);
		border-radius: 6px;
	}
</style>
