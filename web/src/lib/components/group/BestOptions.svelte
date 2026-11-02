<script lang="ts">
	import type { Slot } from '#lib/types';
	import { useFormat } from '#lib/client/format';
	import Button from '#lib/components/ui/Button.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';

	/** The slots that work for everyone, each with a Propose button. */
	interface Props {
		slots: readonly Slot[];
		pendingCount: number;
		onpropose: (slot: Slot) => void;
		onselect?: (slot: Slot) => void;
		/** Shown when the optional AI planner is set up on this server. */
		onplan?: () => void;
		max?: number;
	}
	let { slots, pendingCount, onpropose, onselect, onplan, max = 4 }: Props = $props();

	const fmt = useFormat();
	const upcoming = $derived(slots.filter((s) => new Date(s.end) > new Date()).slice(0, max));
	const note = $derived(pendingCount ? 'Everyone synced is free' : 'Everyone is free');
</script>

<section class="options glass" aria-labelledby="best-options">
	<div class="head">
		<h2 id="best-options">Best options</h2>
		{#if onplan && upcoming.length}
			<Button variant="ghost" size="sm" icon="sparkle" onclick={onplan}>Plan with AI</Button>
		{/if}
	</div>
	{#each upcoming as slot (slot.start)}
		<div class="option">
			<button type="button" class="when" onclick={() => onselect?.(slot)}>
				<span class="label">{slot.label}</span>
				<span class="note">{fmt.current.startsAt(slot.start) || note}</span>
			</button>
			<Button variant="accent" size="sm" onclick={() => onpropose(slot)}>Propose</Button>
		</div>
	{:else}
		<EmptyState compact icon="clock" title="Nothing works yet" message="Try another month, or loosen the timing." />
	{/each}
</section>

<style>
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
