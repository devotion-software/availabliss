<script lang="ts">
	import type { Weekday } from '#lib/types';
	import { summarizeDays } from '#lib/client/requirements';

	interface Props {
		columns: readonly Weekday[];
		compact?: boolean;
	}
	let { columns, compact = false }: Props = $props();
	const daysLabel = $derived(summarizeDays(columns));
</script>

<div class="legend" class:compact>
	<span class="item"><span class="key match"></span>Works for everyone</span>
	<span class="item"><span class="key busy"></span>Someone's busy</span>
	{#if columns.length < 7}
		<span>{compact ? `${daysLabel} only` : `Showing ${daysLabel} only, the days this group's timing can use`}</span>
	{/if}
</div>

<style>
	.legend {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 8px 18px;
		font-size: 12px;
		color: var(--muted);
	}
	.compact {
		gap: 6px 14px;
		font-size: 11px;
		padding: 4px 2px 0;
	}
	.item {
		display: flex;
		align-items: center;
		gap: 6px;
	}
	.key {
		width: 12px;
		height: 12px;
		border-radius: 4px;
	}
	.compact .key {
		width: 10px;
		height: 10px;
		border-radius: 3px;
	}
	.match {
		background: var(--accent);
	}
	.busy {
		background: var(--busy-bg);
		opacity: 0.5;
		border: 1px solid var(--busy-line);
	}
</style>
