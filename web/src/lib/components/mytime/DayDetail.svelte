<script lang="ts">
	import { untrack } from 'svelte';
	import { fly } from 'svelte/transition';
	import { prefersReducedMotion } from 'svelte/motion';
	import type { BusyInterval } from '#lib/types';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import DayTimeGrid from './DayTimeGrid.svelte';
	import type { PlacedInterval } from './parts';
	import { busyRanges, freeSummary } from './days';

	/**
	 * One day of My time: previous / next day, all-day items as chips, and a tall
	 * timeline. Shown in a sheet on phones and beside the list on wide screens.
	 */
	interface Props {
		day: string;
		today: string;
		timeZone: string;
		timed: PlacedInterval[];
		allDay: BusyInterval[];
		/** A heading inside the panel (the sheet shows the date as its own title). */
		title?: string;
		/** The previous day is before the list starts. */
		atStart?: boolean;
		onprev: () => void;
		onnext: () => void;
		onselect: (day: string, from: number, to: number, dragged: boolean) => void;
		onopen: (interval: BusyInterval) => void;
	}
	let { day, today, timeZone, timed, allDay, title, atStart = false, onprev, onnext, onselect, onopen }: Props = $props();

	const summary = $derived(freeSummary(busyRanges(timed, allDay)));

	// Each day slides in from the side it's coming from.
	let dir = $state<1 | -1>(1);
	let lastDay = untrack(() => day);
	$effect.pre(() => {
		const d = day;
		untrack(() => {
			if (d !== lastDay) dir = d > lastDay ? 1 : -1;
			lastDay = d;
		});
	});
	const pageIn = (node: Element) =>
		fly(node, { x: prefersReducedMotion.current ? 0 : dir * 48, duration: prefersReducedMotion.current ? 0 : 220 });

	// Swipe sideways on a touch screen for the previous / next day; the day follows the finger.
	let touch: { x: number; y: number } | null = null;
	let dragX = $state(0);
	const delta = (e: TouchEvent, t: { x: number; y: number }) => ({ dx: e.changedTouches[0].clientX - t.x, dy: e.changedTouches[0].clientY - t.y });
	function touchStart(e: TouchEvent) {
		touch = e.touches.length === 1 ? { x: e.touches[0].clientX, y: e.touches[0].clientY } : null;
	}
	function touchMove(e: TouchEvent) {
		if (!touch) return;
		const { dx, dy } = delta(e, touch);
		dragX = Math.abs(dx) > Math.abs(dy) * 1.5 ? (dx > 0 && atStart ? dx * 0.1 : dx * 0.35) : 0;
	}
	function touchEnd(e: TouchEvent) {
		if (!touch) return;
		const { dx, dy } = delta(e, touch);
		touch = null;
		dragX = 0;
		if (Math.abs(dx) <= 60 || Math.abs(dx) <= Math.abs(dy) * 1.5) return;
		if (dx < 0) onnext();
		else if (!atStart) onprev();
	}
	const labelOf = (i: BusyInterval) => i.label ?? (i.source === 'manual' ? 'Blocked' : 'Busy');
</script>

<div class="detail">
	<div class="nav">
		<IconButton icon="chevron-left" label="Previous day" shape="rounded" size={40} variant="line" disabled={atStart} onclick={onprev} />
		<div class="mid" aria-live="polite">
			{#if title}<h2>{title}</h2>{/if}
			<span class="summary">{summary}</span>
		</div>
		<IconButton icon="chevron-right" label="Next day" shape="rounded" size={40} variant="line" onclick={onnext} />
	</div>

	{#if allDay.length}
		<div class="chips" aria-label="All day">
			{#each allDay as interval, i (i)}
				<button
					type="button"
					class="chip {interval.source}"
					class:ignored={!!interval.ignoreId}
					aria-label="{interval.source === 'manual' ? 'Edit' : 'About'} {labelOf(interval)}, all day"
					onclick={() => onopen(interval)}
				>
					<Icon name={interval.source === 'manual' ? 'lock' : 'calendar-sync'} size={14} />
					<span>{labelOf(interval)}</span>
					<span class="all">All day</span>
				</button>
			{/each}
		</div>
	{/if}

	<div
		class="grid"
		role="group"
		aria-label="Times"
		class:dragging={dragX !== 0}
		style:transform={dragX ? `translateX(${dragX}px)` : undefined}
		ontouchstart={touchStart}
		ontouchmove={touchMove}
		ontouchend={touchEnd}
		ontouchcancel={() => ((touch = null), (dragX = 0))}
	>
		{#key day}
			<div class="page" in:pageIn>
				<DayTimeGrid {day} {timed} {today} {timeZone} {onselect} {onopen} />
			</div>
		{/key}
	</div>
	<p class="hint"><span class="touch">Tap a time to block it</span><span class="mouse">Click a time to block it, or drag to choose a range</span></p>
</div>

<style>
	.grid {
		transition: transform 0.25s ease;
		touch-action: pan-y;
	}
	.grid.dragging {
		transition: none;
	}
	.page {
		height: 100%;
	}
	.detail {
		display: flex;
		flex-direction: column;
		gap: 12px;
		min-height: 0;
		height: 100%;
	}
	.nav {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.mid {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
		gap: 2px;
	}
	h2 {
		font-size: 18px;
		font-weight: 700;
	}
	.summary {
		font-size: 14px;
		font-weight: 600;
		color: var(--muted);
	}
	.chips {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.chip {
		min-height: 44px;
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 0 12px;
		border-radius: 12px;
		font-size: 14px;
		font-weight: 600;
		text-align: left;
		border: 1px solid transparent;
	}
	.chip span:first-of-type {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.all {
		font-size: 12px;
		font-weight: 500;
		opacity: 0.8;
	}
	.chip.manual {
		background: var(--stripe-manual), var(--surface);
		border: 1.5px solid var(--accent);
		color: var(--accent-ink);
	}
	.chip.calendar {
		background: var(--busy-bg);
		border-color: var(--busy-line);
		border-left: 3px solid var(--busy-line);
		color: var(--ink);
	}
	.chip.ignored {
		background: transparent;
		border: 1px dashed var(--busy-line);
		opacity: 0.6;
	}
	.grid {
		flex: 1;
		min-height: 0;
		--grid-h: 100%;
	}
	.hint {
		font-size: 12px;
		color: var(--muted);
		text-align: center;
	}
	/* Touch screens have no mouse drag to mention. */
	.touch {
		display: none;
	}
	@media (hover: none) {
		.touch {
			display: inline;
		}
		.mouse {
			display: none;
		}
	}
</style>
