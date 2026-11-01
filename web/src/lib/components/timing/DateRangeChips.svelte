<script lang="ts">
	import type { DateRange } from '#lib/types';
	import { formatDayRange } from '#lib/client/format';
	import Chip from '#lib/components/ui/Chip.svelte';
	import DateRangeSheet from './DateRangeSheet.svelte';

	/** Editable list of date ranges as removable chips, plus "+ Add" opening a two-date sheet. */
	interface Props {
		ranges: DateRange[];
		readonly?: boolean;
		/** Default start date in the add sheet. */
		start: string;
		addLabel?: string;
		emptyText?: string;
		sheetTitle?: string;
		sheetDescription?: string;
	}
	let {
		ranges = $bindable(),
		readonly = false,
		start,
		addLabel = '+ Add date range',
		emptyText = 'None yet.',
		sheetTitle,
		sheetDescription
	}: Props = $props();

	let adding = $state(false);
	const label = (r: DateRange) => formatDayRange(r.from, r.to, false);
</script>

<div class="chips">
	{#each ranges as range, i (range.from + range.to + i)}
		{#if readonly}
			<span class="tag">{label(range)}</span>
		{:else}
			<Chip onremove={() => (ranges = ranges.filter((_, j) => j !== i))} removeLabel="Remove {label(range)}">{label(range)}</Chip>
		{/if}
	{/each}
	{#if !readonly}
		<Chip size="sm" dashed onclick={() => (adding = true)}>{addLabel}</Chip>
	{:else if ranges.length === 0}
		<p class="plain">{emptyText}</p>
	{/if}
</div>

<DateRangeSheet
	bind:open={adding}
	{start}
	title={sheetTitle}
	description={sheetDescription}
	onadd={(r) => (ranges = [...ranges, r].sort((a, b) => a.from.localeCompare(b.from)))}
/>

<style>
	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}
	.plain {
		font-size: 13px;
		color: var(--muted);
	}
	.tag {
		height: 36px;
		padding: 0 12px;
		border-radius: 999px;
		background: var(--accent-soft);
		color: var(--accent-ink);
		font-size: 13px;
		font-weight: 600;
		display: inline-flex;
		align-items: center;
	}
</style>
