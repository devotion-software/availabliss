<script lang="ts">
	import { fly } from 'svelte/transition';
	import { prefersReducedMotion } from 'svelte/motion';
	import type { BusyInterval } from '#lib/types';
	import { addDays, startOfWeek } from '#lib/client/dates';
	import { dayNumber, formatDayRange, weekdayName } from '#lib/client/format';
	import Icon from '#lib/components/ui/Icon.svelte';
	import DayTimeGrid from './DayTimeGrid.svelte';
	import type { PlacedInterval } from './parts';
	import { busyRanges, clampDay, dayLine, dayMarks, dayNames } from './days';
	import { pageDirection, swipe } from './swipe.svelte';

	/**
	 * My time opened on a day: the day's week as a strip, and that day's tall
	 * timeline. Swipe the strip for the next / previous week, the timeline for
	 * the next / previous day.
	 */
	interface Props {
		day: string;
		today: string;
		/** The last day you can open (a year ahead). */
		last: string;
		timeZone: string;
		/** A day's busy time; null until it has loaded. */
		get: (day: string) => { timed: PlacedInterval[]; allDay: BusyInterval[] } | null;
		/** "Month" or "Agenda": where back goes. */
		backLabel: string;
		onback: () => void;
		onday: (day: string) => void;
		onselect: (day: string, from: number, to: number, dragged: boolean) => void;
		onopen: (interval: BusyInterval) => void;
		onblock: () => void;
	}
	let { day, today, last, timeZone, get, backLabel, onback, onday, onselect, onopen, onblock }: Props = $props();

	const weekStart = $derived(startOfWeek(day));
	const week = $derived(Array.from({ length: 7 }, (_, i) => addDays(weekStart, i)));
	const shown = $derived(get(day) ?? { timed: [], allDay: [] });
	const line = $derived(dayLine(day, today, busyRanges(shown.timed, shown.allDay), dayNames(shown.timed, shown.allDay)));

	const canWeek = (by: 1 | -1) => (by < 0 ? weekStart > today : addDays(weekStart, 7) <= last);
	const stepWeek = (by: 1 | -1) => canWeek(by) && onday(clampDay(addDays(day, by * 7), today, last));
	const canDay = (by: 1 | -1) => (by < 0 ? day > today : day < last);
	const stepDay = (by: 1 | -1) => canDay(by) && onday(addDays(day, by));

	const weekDir = pageDirection(() => weekStart);
	const dayDir = pageDirection(() => day);
	const slide = (node: Element, dir: 1 | -1) =>
		fly(node, { x: prefersReducedMotion.current ? 0 : dir * 48, duration: prefersReducedMotion.current ? 0 : 220 });
	const stripSwipe = swipe({ step: stepWeek, can: canWeek });
	const daySwipe = swipe({ step: stepDay, can: canDay });

	const labelOf = (i: BusyInterval) => i.label ?? (i.source === 'manual' ? 'Blocked' : 'Busy');
</script>

<section class="week-view" aria-labelledby="week-title">
	<div class="bar">
		<button type="button" class="back" onclick={onback}>
			<Icon name="chevron-left" size={18} strokeWidth={2.4} />{backLabel}
		</button>
		<h1 id="week-title">{formatDayRange(weekStart, addDays(weekStart, 6), false)}</h1>
		<button type="button" class="add" onclick={onblock} aria-label="Block time on {weekdayName(day, 'long')} {dayNumber(day)}">
			<Icon name="plus" size={15} strokeWidth={2.6} />Block
		</button>
	</div>

	<div
		role="presentation"
		class="glass strip-wrap"
		class:dragging={stripSwipe.dragX !== 0}
		style:transform={stripSwipe.dragX ? `translateX(${stripSwipe.dragX}px)` : undefined}
		ontouchstart={stripSwipe.start}
		ontouchmove={stripSwipe.move}
		ontouchend={stripSwipe.end}
		ontouchcancel={stripSwipe.cancel}
	>
		{#key weekStart}
			<div class="strip" role="group" aria-label="Days this week" in:slide={weekDir.current}>
				{#each week as d (d)}
					{@const info = get(d)}
					{@const marks = info ? dayMarks(info.timed, info.allDay) : { mine: false, calendar: false }}
					<button
						type="button"
						class="sday"
						class:selected={d === day}
						class:today={d === today}
						disabled={d < today || d > last}
						aria-pressed={d === day}
						aria-current={d === today ? 'date' : undefined}
						aria-label="{weekdayName(d, 'long')} {dayNumber(d)}{d === today ? ', today' : ''}"
						onclick={() => onday(d)}
					>
						<span class="wd">{weekdayName(d)}</span>
						<span class="num">{dayNumber(d)}</span>
						<span class="dots" aria-hidden="true">
							{#if marks.mine}<i class="mine"></i>{/if}
							{#if marks.calendar}<i class="cal"></i>{/if}
						</span>
					</button>
				{/each}
			</div>
		{/key}
	</div>

	<p class="line" aria-live="polite">{line}</p>

	{#if shown.allDay.length}
		<div class="chips" aria-label="All day">
			{#each shown.allDay as interval, i (i)}
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
		class:dragging={daySwipe.dragX !== 0}
		style:transform={daySwipe.dragX ? `translateX(${daySwipe.dragX}px)` : undefined}
		ontouchstart={daySwipe.start}
		ontouchmove={daySwipe.move}
		ontouchend={daySwipe.end}
		ontouchcancel={daySwipe.cancel}
	>
		{#key day}
			<div class="page" in:slide={dayDir.current}>
				<DayTimeGrid {day} timed={shown.timed} {today} {timeZone} {onselect} {onopen} />
			</div>
		{/key}
	</div>
	<p class="hint"><span class="touch">Tap a time to block it</span><span class="mouse">Click a time to block it, or drag to choose a range</span></p>
</section>

<style>
	.week-view {
		display: flex;
		flex-direction: column;
		gap: 12px;
		/* Fills the screen above the tab bar; the timeline takes what's left and scrolls. */
		height: calc(100dvh - max(20px, env(safe-area-inset-top)) - var(--tabbar-space) - env(safe-area-inset-bottom));
		min-height: 560px;
	}
	.bar {
		display: grid;
		grid-template-columns: 1fr auto 1fr;
		align-items: center;
		gap: 8px;
		min-height: 44px;
	}
	.back,
	.add {
		display: inline-flex;
		align-items: center;
		gap: 3px;
		min-height: 40px;
		padding: 0 12px 0 6px;
		border-radius: 999px;
		font-size: 15px;
		font-weight: 650;
		color: var(--accent-ink);
		justify-self: start;
	}
	.back:hover,
	.add:hover {
		background: var(--accent-soft);
	}
	.add {
		justify-self: end;
		padding: 0 12px;
		background: var(--accent-soft);
	}
	h1 {
		font-size: 17px;
		font-weight: 700;
		white-space: nowrap;
	}

	.strip-wrap {
		border-radius: 22px;
		padding: 6px;
		transition: transform 0.25s ease;
		touch-action: pan-y;
		overflow: hidden;
	}
	.strip-wrap.dragging,
	.grid.dragging {
		transition: none;
	}
	.strip {
		display: grid;
		grid-template-columns: repeat(7, minmax(0, 1fr));
		gap: 4px;
	}
	.sday {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
		padding: 7px 0 6px;
		border-radius: 16px;
		color: var(--ink);
		min-height: 64px;
	}
	.sday:not(:disabled):not(.selected):hover {
		background: var(--surface);
	}
	.sday:disabled {
		opacity: 0.35;
		cursor: default;
	}
	.wd {
		font-size: 11px;
		font-weight: 600;
		color: var(--muted);
	}
	.num {
		font-size: 17px;
		font-weight: 750;
		font-variant-numeric: tabular-nums;
	}
	.today .num {
		color: var(--accent-ink);
	}
	.sday.selected {
		background: var(--solid);
		color: var(--on-solid);
	}
	.sday.selected .wd,
	.sday.selected .num {
		color: var(--on-solid);
	}
	.dots {
		display: flex;
		gap: 3px;
		height: 5px;
	}
	.dots i {
		width: 5px;
		height: 5px;
		border-radius: 50%;
	}
	.dots .mine {
		background: var(--accent);
	}
	.dots .cal {
		background: var(--busy-line);
	}
	.selected .dots .mine {
		background: color-mix(in srgb, var(--accent) 60%, var(--on-solid));
	}

	.line {
		font-size: 14px;
		font-weight: 600;
		color: var(--muted);
		padding: 0 4px;
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
		transition: transform 0.25s ease;
		touch-action: pan-y;
	}
	.page {
		height: 100%;
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
	@media (min-width: 900px) {
		.week-view {
			/* Below the app's top bar. */
			height: calc(100dvh - 150px);
		}
		h1 {
			font-size: 20px;
		}
	}
</style>
