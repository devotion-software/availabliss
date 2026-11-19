<script lang="ts">
	import type { Proposal, ResponseKind } from '#lib/types';
	import { respondToProposal, type Reply } from '#lib/client/proposals';
	import { toasts } from '#lib/client/toast.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';

	/**
	 * "✓ You're in ⌄": your reply as a chip in the bottom-right corner of a proposal (or a confirmed
	 * plan) on the Plans page, matching the apps' calendar chip, to change it without opening it.
	 * Only for people who were asked (members who joined since weren't).
	 */
	interface Props {
		proposal: Proposal;
		meId: string;
		/** The updated proposal, once saved. */
		onchanged?: (proposal: Proposal) => void;
	}
	let { proposal, meId, onchanged }: Props = $props();

	const LABEL: Record<ResponseKind, string> = { accepted: 'You’re in', maybe: 'You said maybe', declined: 'You can’t make it', pending: 'Reply' };
	const ICON = { accepted: 'check', maybe: 'info', declined: 'x', pending: 'reply' } as const;
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
	<label class="reply {mine}">
		<Icon name={ICON[mine]} size={13} strokeWidth={2.6} />
		<select value={mine} disabled={busy} onchange={change} aria-label="Your reply to {proposal.title ?? 'this proposal'}">
			{#if mine === 'pending'}<option value="pending" disabled>{LABEL.pending}</option>{/if}
			<option value="accepted">{LABEL.accepted}</option>
			<option value="maybe">{LABEL.maybe}</option>
			<option value="declined">{LABEL.declined}</option>
		</select>
		<Icon name="chevron-down" size={12} class="chev" />
	</label>
{/if}

<style>
	/* A chip that hugs its content, like the apps' "In Work" calendar chip. */
	.reply {
		position: relative;
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 28px;
		padding: 0 10px;
		border: 1px solid var(--line);
		border-radius: 999px;
		background: var(--surface);
		font-size: 13px;
		font-weight: 500;
		color: var(--ink);
		white-space: nowrap;
	}
	.reply > :global(svg) {
		flex: none;
	}
	.accepted > :global(svg:first-child) {
		color: var(--ok-ink);
	}
	.maybe > :global(svg:first-child) {
		color: var(--warn-ink);
	}
	.declined > :global(svg:first-child) {
		color: var(--muted);
	}
	.pending {
		color: var(--accent-ink);
	}
	.reply :global(.chev) {
		color: var(--muted);
		pointer-events: none;
	}
	select {
		font: inherit;
		color: inherit;
		background: transparent;
		border: 0;
		padding: 0;
		appearance: none;
		/* As wide as the chosen reply, not the longest option. */
		field-sizing: content;
		cursor: pointer;
	}
	.reply:has(select:focus-visible) {
		outline: 2px solid var(--accent);
		outline-offset: 2px;
	}
	select:focus-visible {
		outline: none;
	}
</style>
