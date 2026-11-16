<script lang="ts">
	import { fly } from 'svelte/transition';
	import { prefersReducedMotion } from 'svelte/motion';
	import { addDays, addMonths, startOfMonth } from '#lib/client/dates';
	import { dayNumber, monthTitle } from '#lib/client/format';
	import { WEEKDAY_NAMES } from '#lib/client/requirements';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import { monthCells, type FreeLevel } from './days';
	import { pageDirection, swipe } from './swipe.svelte';

	/**
	 * My time's month: every day filled by how free it is (the accent gets
	 * stronger the more of 06:00 – 24:00 is free). Tap a day to open its week.
	 */
	interface Props {
		/** First of the month on screen. */
		month: string;
		today: string;
		/** The last day you can open (a year ahead). */
		last: string;
		/** How free a loaded day is; null until its busy time has loaded. */
		info: (day: string) => { level: FreeLevel; label: string; mine: boolean } | null;
		onmonth: (month: string) => void;
		onselect: (day: string) => void;
	}
	let { month, today, last, info, onmonth, onselect }: Props = $props();

	const first = $derived(startOfMonth(today));
	const lastMonth = $derived(startOfMonth(last));
	const cells = $derived(monthCells(month));
	const can = (by: 1 | -1) => (by < 0 ? month > first : month < lastMonth);
	const step = (by: 1 | -1) => can(by) && onmonth(addMonths(month, by));

	const dir = pageDirection(() => month);
	const pageIn = (node: Element) =>
		fly(node, { x: prefersReducedMotion.current ? 0 : dir.current * 48, duration: prefersReducedMotion.current ? 0 : 220 });
	const swiper = swipe({ step, can });

	let grid = $state<HTMLElement>();
	// Arrow keys move between days like a grid.
	function onkeydown(e: KeyboardEvent) {
		const moves: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };
		const by = moves[e.key];
		if (!by || !grid) return;
		const at = (document.activeElement as HTMLElement | null)?.dataset.day;
		if (!at) return;
		e.preventDefault();
		grid.querySelector<HTMLElement>(`button[data-day="${addDays(at, by)}"]`)?.focus();
	}
</script>

<section class="month" aria-labelledby="month-heading">
	<div class="head">
		<h2 id="month-heading" aria-live="polite">{monthTitle(month)}</h2>
		<span class="nav">
			<IconButton icon="chevron-left" label="Previous month" variant="plain" shape="rounded" size={36} disabled={!can(-1)} onclick={() => step(-1)} />
			<IconButton icon="chevron-right" label="Next month" variant="plain" shape="rounded" size={36} disabled={!can(1)} onclick={() => step(1)} />
		</span>
		{#if month !== first}<button type="button" class="today-btn" onclick={() => onmonth(first)}>Today</button>{/if}
	</div>

	<div class="heads" aria-hidden="true">
		{#each WEEKDAY_NAMES as name (name)}<span>{name.slice(0, 3)}</span>{/each}
	</div>

	<div
		role="presentation"
		class="body"
		class:dragging={swiper.dragX !== 0}
		style:transform={swiper.dragX ? `translateX(${swiper.dragX}px)` : undefined}
		ontouchstart={swiper.start}
		ontouchmove={swiper.move}
		ontouchend={swiper.end}
		ontouchcancel={swiper.cancel}
	>
		{#key month}
			<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
			<div class="cells" role="group" aria-label="Days" bind:this={grid} {onkeydown} in:pageIn>
				{#each cells as day, i (day ?? `blank-${i}`)}
					{#if !day}
						<span class="cell blank" aria-hidden="true"></span>
					{:else if day < today || day > last}
						<span class="cell l0 past" aria-hidden="true"><span class="num">{dayNumber(day)}</span></span>
					{:else}
						{@const d = info(day)}
						<button
							type="button"
							data-day={day}
							class="cell l{d?.level ?? 0}"
							class:today={day === today}
							aria-current={day === today ? 'date' : undefined}
							aria-label={d?.label ?? day}
							onclick={() => onselect(day)}
						>
							<span class="num">{dayNumber(day)}</span>
							<i class="dot" class:on={d?.mine}></i>
						</button>
					{/if}
				{/each}
			</div>
		{/key}
	</div>

	<div class="legend" aria-hidden="true">
		<span>Less free</span>
		<i class="cell l0"></i><i class="cell l1"></i><i class="cell l2"></i><i class="cell l3"></i>
		<span>More free</span>
	</div>
</section>

<style>
	.month {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.head {
		display: flex;
		align-items: center;
		gap: 4px;
		min-height: 40px;
	}
	h2 {
		font-size: 19px;
		font-weight: 650;
		white-space: nowrap;
		margin-right: 4px;
	}
	.nav {
		display: flex;
	}
	/* "Today": back to today's month (as in the group calendar). */
	.today-btn {
		margin-left: auto;
		padding: 5px 11px;
		border: 0;
		border-radius: 999px;
		background: var(--accent-soft);
		color: var(--accent-ink);
		font: inherit;
		font-size: 13px;
		font-weight: 650;
		cursor: pointer;
	}
	.today-btn:hover {
		filter: brightness(0.97);
	}

	.heads,
	.cells {
		display: grid;
		grid-template-columns: repeat(7, minmax(0, 1fr));
		gap: 5px;
	}
	.heads span {
		text-align: center;
		font-size: 11px;
		font-weight: 600;
		color: var(--muted);
	}
	.body {
		transition: transform 0.25s ease;
		touch-action: pan-y;
	}
	.body.dragging {
		transition: none;
	}

	.cell {
		position: relative;
		height: 46px;
		min-width: 0;
		border-radius: 14px;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		border: 1px solid transparent;
		color: var(--ink);
		font-variant-numeric: tabular-nums;
		transition:
			transform 0.15s ease,
			background 0.2s ease;
	}
	button.cell:hover {
		transform: scale(1.04);
	}
	button.cell:focus-visible {
		outline: 2px solid var(--ink);
		outline-offset: 2px;
	}
	.blank {
		visibility: hidden;
	}
	.num {
		font-size: 15px;
		font-weight: 650;
		line-height: 1;
		padding: 3px 6px;
		border-radius: 999px;
	}
	/* Out of the flow so the number alone is centred, hanging just below it. */
	.dot {
		position: absolute;
		top: calc(50% + 12px);
		left: calc(50% - 2px);
		width: 4px;
		height: 4px;
		border-radius: 50%;
		background: currentColor;
		visibility: hidden;
	}
	.dot.on {
		visibility: visible;
	}

	/* Free-ness, least to most. */
	.l0 {
		background: var(--glass);
		border-color: var(--line-2);
		color: var(--muted);
	}
	.l1 {
		background: color-mix(in srgb, var(--accent) 20%, var(--surface));
		color: var(--ink);
	}
	.l2 {
		background: color-mix(in srgb, var(--accent) 50%, var(--surface));
		color: var(--on-accent);
	}
	.l3 {
		background: var(--accent);
		color: var(--on-accent);
	}
	.past {
		opacity: 0.4;
	}
	/* Today: the number on an accent pill, inverted on a cell already strong with the accent. */
	.today .num {
		background: var(--accent);
		color: var(--on-accent);
	}
	.today.l2 .num,
	.today.l3 .num {
		background: var(--on-accent);
		color: var(--accent);
	}

	.legend {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 5px;
		margin-top: 4px;
		font-size: 12px;
		color: var(--muted);
	}
	.legend span {
		margin: 0 4px;
	}
	.legend .cell {
		width: 18px;
		height: 18px;
		border-radius: 6px;
	}

	@media (min-width: 900px) {
		.cells {
			gap: 6px;
		}
		.cell {
			height: 64px;
			border-radius: 16px;
		}
		.num {
			font-size: 16px;
		}
		h2 {
			font-size: 20px;
		}
	}
</style>
