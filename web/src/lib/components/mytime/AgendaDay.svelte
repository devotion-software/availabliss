<script lang="ts">
	import type { BusyInterval } from '#lib/types';
	import { dayNumber, weekdayName } from '#lib/client/format';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { clock } from './parts';
	import { isWholeDay, type AgendaItem, type Range } from './days';

	/**
	 * One day in My time's agenda: the date and its free summary (opens the
	 * week), each busy item, then the longest free stretch, ready to block.
	 */
	interface Props {
		day: string;
		today: string;
		/** "Free from 17:30": also what the free row says. */
		summary: string;
		/** What a screen reader hears for the header. */
		label: string;
		items: AgendaItem[];
		/** The longest free stretch; null when busy all day. */
		free: Range | null;
		onday: () => void;
		onopen: (interval: BusyInterval) => void;
		/** Block the free stretch (null = the whole day). */
		onblock: (range: Range | null) => void;
	}
	let { day, today, summary, label, items, free, onday, onopen, onblock }: Props = $props();
</script>

<div class="day" id="day-{day}">
	<button type="button" class="head" aria-label={label} onclick={onday}>
		<span class="date" aria-hidden="true">
			<span class="wd">{weekdayName(day)}</span>
			<span class="num" class:today={day === today}>{dayNumber(day)}</span>
			{#if day === today}<span class="tag-today">Today</span>{/if}
		</span>
		<span class="summary" aria-hidden="true">{summary}</span>
		<Icon name="chevron-right" size={15} class="chev" />
	</button>

	{#each items as item, i (i)}
		<button
			type="button"
			class="item {item.interval.source}"
			aria-label="{item.interval.source === 'manual' ? 'Edit' : 'About'} {item.title}, {item.range
				? `${clock(item.range.from)} to ${clock(item.range.to)}`
				: 'all day'}, {item.tag}"
			onclick={() => onopen(item.interval)}
		>
			<span class="when" aria-hidden="true">
				{#if item.range}
					<span>{clock(item.range.from)}</span>
					<span class="to">{clock(item.range.to)}</span>
				{:else}
					<span>All day</span>
				{/if}
			</span>
			<span class="title" aria-hidden="true">{item.title}</span>
			<span class="src" aria-hidden="true">{item.tag}</span>
		</button>
	{/each}

	{#if free}
		<button type="button" class="free" aria-label="{summary}. Block this time" onclick={() => onblock(isWholeDay(free) ? null : free)}>
			<span class="free-text">{summary}</span>
			<span class="add"><Icon name="plus" size={13} strokeWidth={2.6} />Block</span>
		</button>
	{/if}
</div>

<style>
	.day {
		display: flex;
		flex-direction: column;
		gap: 6px;
		padding: 6px;
		scroll-margin: 96px 0 calc(var(--tabbar-space) + 16px);
	}
	.head {
		display: flex;
		align-items: center;
		gap: 10px;
		min-height: 40px;
		padding: 2px 6px;
		border-radius: 12px;
		text-align: left;
		color: var(--ink);
	}
	.head:hover {
		background: color-mix(in srgb, var(--surface) 70%, transparent);
	}
	.date {
		flex: 1;
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 15px;
		font-weight: 650;
	}
	.num {
		font-variant-numeric: tabular-nums;
		padding: 2px 7px;
		margin-left: -3px;
		border-radius: 999px;
	}
	.num.today {
		background: var(--accent);
		color: var(--on-accent);
		margin-left: 0;
	}
	.tag-today {
		font-size: 13px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	.summary {
		font-size: 13px;
		font-weight: 600;
		color: var(--muted);
		white-space: nowrap;
	}
	.head :global(.chev) {
		color: var(--muted);
		flex: none;
	}

	.item,
	.free {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 48px;
		padding: 7px 12px;
		border-radius: 14px;
		text-align: left;
		color: var(--ink);
		border: 1px solid transparent;
	}
	.item.manual {
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	.item.calendar {
		background: var(--surface);
		border-color: var(--glass-border);
	}
	.item:hover {
		filter: brightness(0.98);
	}
	.when {
		flex: none;
		width: 46px;
		display: flex;
		flex-direction: column;
		font-size: 13px;
		font-weight: 650;
		line-height: 1.2;
		font-variant-numeric: tabular-nums;
	}
	.to {
		font-weight: 500;
		opacity: 0.75;
	}
	.title {
		flex: 1;
		min-width: 0;
		font-size: 14px;
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.src {
		flex: none;
		font-size: 11px;
		font-weight: 600;
		color: var(--muted);
	}
	.free {
		border: 1.5px dashed var(--line-3);
		background: transparent;
		color: var(--muted);
		min-height: 44px;
	}
	.free:hover {
		border-color: var(--accent);
	}
	.free-text {
		flex: 1;
		font-size: 14px;
		font-weight: 600;
	}
	.add {
		display: inline-flex;
		align-items: center;
		gap: 3px;
		font-size: 13px;
		font-weight: 650;
		color: var(--accent-ink);
	}
</style>
