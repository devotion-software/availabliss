<script lang="ts">
	import { onMount } from 'svelte';
	import type { BusyInterval } from '#lib/types';
	import { minutesIntoDay } from '#lib/client/dates';
	import { dayNumber, weekdayName } from '#lib/client/format';
	import { clock, DAY_MINUTES, initialScrollMinutes, selectionRange, tapRange, type PlacedInterval, type WeekLayout } from './parts';

	/**
	 * Week view of your busy time: days across the top, hours down the side,
	 * every interval at its exact times. Tap an empty slot (or drag with a mouse)
	 * to block that time; tap busy time to edit or inspect it.
	 */
	interface Props {
		layout: WeekLayout;
		today: string;
		timeZone: string;
		/** A tap (an hour) or a drag (exact range) on empty time. */
		onselect: (day: string, from: number, to: number, dragged: boolean) => void;
		/** A tap on busy time: your own block, or an interval from a calendar. */
		onopen: (interval: BusyInterval) => void;
	}
	let { layout, today, timeZone, onselect, onopen }: Props = $props();

	const HOURS = Array.from({ length: 24 }, (_, h) => h);
	const hasAllDay = $derived([...layout.allDay.values()].some((list) => list.length > 0));

	let scroller = $state<HTMLDivElement>();
	let now = $state(Date.now());

	onMount(() => {
		const timer = setInterval(() => (now = Date.now()), 60_000);
		return () => clearInterval(timer);
	});

	// Open on the working day (or the earliest busy time) whenever the week changes.
	let scrolledFor = '';
	$effect(() => {
		const week = layout.days[0];
		if (!scroller || scrolledFor === week) return;
		scrolledFor = week;
		const hourPx = scroller.scrollHeight / 24;
		scroller.scrollTop = (initialScrollMinutes(layout) / 60) * hourPx;
	});

	const nowMinutes = $derived(layout.days.includes(today) ? minutesIntoDay(new Date(now).toISOString(), today, timeZone) : null);

	// ── Selecting empty time ────────────────────────────────────────────────
	let drag = $state<{ day: string; anchor: number; current: number; pointerId: number; moved: boolean } | null>(null);
	const selection = $derived(drag?.moved ? { day: drag.day, ...selectionRange(drag.anchor, drag.current) } : null);

	function minutesAt(e: PointerEvent, column: HTMLElement): number {
		const rect = column.getBoundingClientRect();
		return Math.min(DAY_MINUTES, Math.max(0, ((e.clientY - rect.top) / rect.height) * DAY_MINUTES));
	}

	function down(e: PointerEvent, day: string) {
		if (e.button !== 0 || (e.target as HTMLElement).closest('.event')) return;
		const column = e.currentTarget as HTMLElement;
		const at = minutesAt(e, column);
		drag = { day, anchor: at, current: at, pointerId: e.pointerId, moved: false };
		// Mouse drags select a range; touch keeps scrolling the grid.
		if (e.pointerType === 'mouse') column.setPointerCapture(e.pointerId);
	}

	function move(e: PointerEvent) {
		if (!drag || e.pointerId !== drag.pointerId || e.pointerType !== 'mouse') return;
		const at = minutesAt(e, e.currentTarget as HTMLElement);
		drag.current = at;
		if (Math.abs(at - drag.anchor) >= 10) drag.moved = true;
	}

	function up(e: PointerEvent) {
		if (!drag || e.pointerId !== drag.pointerId) return;
		const { day, anchor, current, moved } = drag;
		drag = null;
		if (moved) {
			const r = selectionRange(anchor, current);
			onselect(day, r.from, r.to, true);
		} else {
			const r = tapRange(anchor);
			onselect(day, r.from, r.to, false);
		}
	}

	function cancel() {
		drag = null;
	}

	// ── Drawing ─────────────────────────────────────────────────────────────
	const top = (m: number) => `${(m / DAY_MINUTES) * 100}%`;

	function boxStyle(p: PlacedInterval): string {
		const width = 100 / p.lanes;
		return `top:${top(p.from)};height:${((p.to - p.from) / DAY_MINUTES) * 100}%;left:calc(${p.lane * width}% + 2px);width:calc(${width}% - 4px)`;
	}

	const labelOf = (i: BusyInterval) => i.label ?? 'Busy';

	function describe(p: PlacedInterval): string {
		const what = p.interval.source === 'calendar' ? `Busy from ${p.interval.label ?? 'a calendar'}` : `Blocked by you${p.interval.label ? `: ${p.interval.label}` : ''}`;
		return `${what}, ${weekdayName(p.day, 'long')} ${clock(p.from)} to ${clock(p.to)}`;
	}
</script>

<div class="wtg" style="--cols: {layout.days.length}">
	<div class="row head" aria-hidden="true">
		<span class="gutter"></span>
		{#each layout.days as day (day)}
			<span class="dayhead" class:today={day === today}>
				<span class="dname">{weekdayName(day)}</span>
				<span class="dnum">{dayNumber(day)}</span>
			</span>
		{/each}
	</div>

	{#if hasAllDay}
		<div class="row allday">
			<span class="gutter label">All day</span>
			{#each layout.days as day (day)}
				<div class="allday-cell">
					{#each layout.allDay.get(day) ?? [] as interval, i (i)}
						<button
							type="button"
							class="chip {interval.source}"
							title={labelOf(interval)}
							aria-label="{interval.source === 'manual' ? 'Edit' : 'About'} {labelOf(interval)}, all day {weekdayName(day, 'long')}"
							onclick={() => onopen(interval)}>{labelOf(interval)}</button
						>
					{/each}
				</div>
			{/each}
		</div>
	{/if}

	<div class="scroller" bind:this={scroller}>
		<div class="body">
			<div class="gutter hours" aria-hidden="true">
				{#each HOURS as h (h)}
					{#if h > 0}<span class="hour" style="top: {top(h * 60)}">{String(h).padStart(2, '0')}:00</span>{/if}
				{/each}
			</div>
			{#each layout.days as day (day)}
				<div
					class="column"
					class:today={day === today}
					role="group"
					aria-label="{weekdayName(day, 'long')} {dayNumber(day)}"
					onpointerdown={(e) => down(e, day)}
					onpointermove={move}
					onpointerup={up}
					onpointercancel={cancel}
				>
					{#each layout.timed.get(day) ?? [] as p, i (i)}
						{@const minutes = p.to - p.from}
						<button
							type="button"
							class="event {p.interval.source}"
							class:cont-before={p.continuesBefore}
							class:cont-after={p.continuesAfter}
							style={boxStyle(p)}
							title={describe(p)}
							aria-label="{p.interval.source === 'manual' ? 'Edit: ' : ''}{describe(p)}"
							onclick={() => onopen(p.interval)}
						>
							<span class="elabel">{labelOf(p.interval)}</span>
							{#if minutes >= 45}<span class="etime">{clock(p.from)} – {clock(p.to)}</span>{/if}
						</button>
					{/each}

					{#if selection && selection.day === day}
						<div class="selection" style="top:{top(selection.from)};height:{((selection.to - selection.from) / DAY_MINUTES) * 100}%">
							{clock(selection.from)} – {clock(selection.to)}
						</div>
					{/if}

					{#if day === today && nowMinutes !== null && nowMinutes >= 0 && nowMinutes <= DAY_MINUTES}
						<div class="now" style="top: {top(nowMinutes)}" aria-hidden="true"></div>
					{/if}
				</div>
			{/each}
		</div>
	</div>
</div>

<style>
	.wtg {
		--gutter-w: 38px;
		--hour: 44px;
		display: flex;
		flex-direction: column;
		min-width: 0;
		user-select: none;
	}
	.row,
	.body {
		display: grid;
		grid-template-columns: var(--gutter-w) repeat(var(--cols), minmax(0, 1fr));
	}
	.head {
		padding-bottom: 8px;
		border-bottom: 1px solid var(--line);
	}
	.dayhead {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
		line-height: 1.1;
	}
	.dname {
		font-size: 11px;
		font-weight: 600;
		color: var(--muted);
	}
	.dnum {
		font-family: var(--font-display);
		font-size: 17px;
		font-weight: 650;
		width: 30px;
		height: 30px;
		display: grid;
		place-items: center;
		border-radius: 999px;
	}
	.dayhead.today .dname {
		color: var(--accent-ink);
	}
	.dayhead.today .dnum {
		background: var(--accent);
		color: var(--on-accent);
	}

	.allday {
		padding: 6px 0;
		border-bottom: 1px solid var(--line);
	}
	.label {
		font-size: 10px;
		font-weight: 600;
		color: var(--muted);
		align-self: center;
		line-height: 1.1;
	}
	.allday-cell {
		display: flex;
		flex-direction: column;
		gap: 3px;
		padding: 0 2px;
		min-width: 0;
	}
	.chip {
		font-size: 10px;
		font-weight: 600;
		border-radius: 6px;
		padding: 3px 5px;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.scroller {
		max-height: min(62dvh, 640px);
		overflow-y: auto;
		overscroll-behavior: contain;
		padding-top: 8px;
		scrollbar-width: thin;
	}
	.body {
		position: relative;
		height: calc(var(--hour) * 24);
	}
	.hours {
		position: relative;
	}
	.hour {
		position: absolute;
		right: 6px;
		transform: translateY(-50%);
		font-size: 10px;
		font-variant-numeric: tabular-nums;
		color: var(--muted);
	}
	.column {
		position: relative;
		border-left: 1px solid var(--line);
		/* Hour lines, with a fainter half-hour line. */
		background:
			repeating-linear-gradient(to bottom, var(--line) 0 1px, transparent 1px var(--hour)),
			repeating-linear-gradient(to bottom, transparent 0 calc(var(--hour) / 2), color-mix(in srgb, var(--line) 50%, transparent) calc(var(--hour) / 2) calc(var(--hour) / 2 + 1px), transparent calc(var(--hour) / 2 + 1px) var(--hour));
		cursor: cell;
		touch-action: pan-y;
	}
	.column.today {
		background-color: color-mix(in srgb, var(--accent-soft) 35%, transparent);
	}

	.event,
	.chip {
		border: 1px solid transparent;
	}
	.event {
		position: absolute;
		border-radius: 8px;
		padding: 3px 5px;
		overflow: hidden;
		display: flex;
		flex-direction: column;
		gap: 1px;
		font-size: 11px;
		line-height: 1.2;
		min-height: 14px;
		text-align: left;
		align-items: stretch;
		justify-content: flex-start;
		cursor: pointer;
		transition:
			transform 0.12s ease,
			box-shadow 0.15s ease;
	}
	.event:hover,
	.chip:hover {
		box-shadow: 0 6px 16px -10px var(--shadow);
	}
	.event:active {
		transform: scale(0.98);
	}
	.chip {
		cursor: pointer;
		text-align: left;
	}
	.manual {
		background: var(--stripe-manual), var(--surface);
		border: 1.5px solid var(--accent);
		color: var(--accent-ink);
	}
	.calendar {
		background: var(--busy-bg);
		border-color: var(--busy-line);
		border-left: 3px solid var(--busy-line);
		color: var(--ink);
	}
	.event.calendar {
		backdrop-filter: blur(6px);
	}
	.cont-before {
		border-top-left-radius: 2px;
		border-top-right-radius: 2px;
	}
	.cont-after {
		border-bottom-left-radius: 2px;
		border-bottom-right-radius: 2px;
	}
	.elabel {
		font-weight: 650;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.etime {
		font-size: 10px;
		opacity: 0.8;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		font-variant-numeric: tabular-nums;
	}

	.selection {
		position: absolute;
		left: 2px;
		right: 2px;
		border-radius: 8px;
		background: var(--accent-soft);
		border: 1.5px dashed var(--accent);
		color: var(--accent-ink);
		font-size: 10px;
		font-weight: 650;
		padding: 3px 5px;
		pointer-events: none;
		font-variant-numeric: tabular-nums;
		overflow: hidden;
		white-space: nowrap;
	}

	.now {
		position: absolute;
		left: -1px;
		right: 0;
		height: 2px;
		background: var(--warn);
		pointer-events: none;
	}
	.now::before {
		content: '';
		position: absolute;
		left: -4px;
		top: -3px;
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: var(--warn);
	}

	@media (min-width: 900px) {
		.wtg {
			--gutter-w: 48px;
			--hour: 48px;
		}
		.event {
			font-size: 12px;
			padding: 4px 6px;
		}
		.etime {
			font-size: 11px;
		}
		.chip {
			font-size: 11px;
		}
	}
</style>
