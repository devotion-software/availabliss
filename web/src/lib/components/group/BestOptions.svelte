<script lang="ts">
	import type { Member, Slot } from '#lib/types';
	import { useFormat } from '#lib/client/format';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { fairLine, missingLine, rankSlots } from './best';
	import Button from '#lib/components/ui/Button.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';

	/**
	 * The slots that work (for everyone, or most of the group with a quorum), each with a Propose
	 * button. With Pro scores, the top one is "Best for the group".
	 */
	interface Props {
		slots: readonly Slot[];
		/** For "Ana can't make it" and "Fair for Ana". */
		members?: Map<string, Member>;
		pendingCount: number;
		onpropose: (slot: Slot) => void;
		onselect?: (slot: Slot) => void;
		/** Shown when the optional AI planner is set up on this server. */
		onplan?: () => void;
		max?: number;
	}
	let { slots, members = new Map(), pendingCount, onpropose, onselect, onplan, max = 4 }: Props = $props();

	const fmt = useFormat();
	// Fewer people missing first, then the best date score (Pro), then preferences, then soonest.
	const upcoming = $derived(rankSlots(slots.filter((s) => new Date(s.end) > new Date())).slice(0, max));
	const scored = $derived(upcoming.some((s) => s.score !== undefined));
	const note = $derived(pendingCount ? 'Everyone synced is free' : 'Everyone is free');
</script>

<section class="options glass" aria-labelledby="best-options">
	<div class="head">
		<h2 id="best-options">Best options</h2>
		{#if onplan && upcoming.length}
			<Button variant="ghost" size="sm" icon="sparkle" onclick={onplan}>Plan with AI</Button>
		{/if}
	</div>
	{#each upcoming as slot, i (slot.start)}
		{@const missing = missingLine(slot.missingMemberIds ?? [], members)}
		{@const fair = fairLine(slot.fairFor, members)}
		{@const best = scored && i === 0}
		<div class="option" class:best>
			<button type="button" class="when" onclick={() => onselect?.(slot)}>
				{#if best}<span class="badge"><Icon name="sparkle" size={12} /> Best for the group</span>{/if}
				<span class="label">{slot.label}</span>
				<span class="note"
					>{fmt.current.startsAt(slot.start) || (missing ? 'Most of the group is free' : note)}{#if slot.preferredBy?.length}<span class="prefers">
							· {slot.preferredBy.length} {slot.preferredBy.length === 1 ? 'prefers' : 'prefer'}</span
						>{/if}</span
				>
				{#if missing}<span class="missing">{missing}</span>{/if}
				{#if fair}<span class="fair">{fair}</span>{/if}
			</button>
			<Button variant="accent" size="sm" onclick={() => onpropose(slot)}>Propose</Button>
		</div>
	{:else}
		<EmptyState compact icon="clock" title="Nothing works yet" message="Try another month, or loosen the timing." />
	{/each}
</section>

<style>
	.best {
		box-shadow: inset 0 0 0 1.5px var(--accent);
	}
	.badge {
		align-self: flex-start;
		display: inline-flex;
		align-items: center;
		gap: 4px;
		margin-bottom: 2px;
		font-size: 11px;
		font-weight: 700;
		color: var(--accent-ink);
	}
	.missing {
		font-size: 12px;
		font-weight: 600;
		color: var(--warn-ink);
	}
	.fair {
		font-size: 12px;
		color: var(--accent-ink);
	}
	.prefers {
		color: var(--accent-ink);
		font-weight: 600;
	}
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
	}
	.options {
		border-radius: 28px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	h2 {
		font-size: 17px;
		font-weight: 650;
	}
	.option {
		border-radius: 18px;
		padding: 12px;
		background: var(--surface);
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.when {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
		text-align: left;
		border-radius: 10px;
	}
	.label {
		font-size: 15px;
		font-weight: 600;
	}
	.note {
		font-size: 12px;
		color: var(--muted);
	}
</style>
