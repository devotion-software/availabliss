<script lang="ts">
	import { onMount } from 'svelte';
	import type { BusyInterval } from '#lib/types';
	import { minutesIntoDay } from '#lib/client/dates';
	import { weekdayName } from '#lib/client/format';
	import { clock, DAY_MINUTES, selectionRange, tapRange, type PlacedInterval } from './parts';

	/**
	 * One day of your busy time, hours down the side, every interval at its exact
	 * times. Tap empty time (or drag with a mouse) to block it; tap busy time to
	 * edit or inspect it.
	 */
	interface Props {
		day: string;
		/** Timed intervals on `day`, with lanes. */
		timed: PlacedInterval[];
		today: string;
		timeZone: string;
		/** Minutes to scroll to whenever the day changes. */
		scrollTo?: number;
		/** A tap (an hour) or a drag (exact range) on empty time. */
		onselect: (day: string, from: number, to: number, dragged: boolean) => void;
		/** A tap on busy time: your own block, or an interval from a calendar. */
		onopen: (interval: BusyInterval) => void;
	}
	let { day, timed, today, timeZone, scrollTo = 8 * 60, onselect, onopen }: Props = $props();

	const HOURS = Array.from({ length: 24 }, (_, h) => h);

	let scroller = $state<HTMLDivElement>();
	let now = $state(Date.now());

	onMount(() => {
		const timer = setInterval(() => (now = Date.now()), 60_000);
		return () => clearInterval(timer);
	});

	// Open on the morning whenever the day changes.
	let scrolledFor = '';
	$effect(() => {
		const el = scroller;
		if (!el || scrolledFor === day) return;
		const target = day;
		// In a sheet the grid can mount before it's laid out: wait for a height to scroll by.
		let frame = 0;
		let tries = 0;
		const apply = () => {
			if (el.scrollHeight <= el.clientHeight && tries++ < 60) return void (frame = requestAnimationFrame(apply));
			scrolledFor = target;
			el.scrollTop = (scrollTo / 60) * (el.scrollHeight / 24);
		};
		apply();
		return () => cancelAnimationFrame(frame);
	});

	const nowMinutes = $derived(day === today ? minutesIntoDay(new Date(now).toISOString(), today, timeZone) : null);

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
		return `top:${top(p.from)};height:${((p.to - p.from) / DAY_MINUTES) * 100}%;left:calc(${p.lane * width}% + 4px);width:calc(${width}% - 8px)`;
	}

	const labelOf = (i: BusyInterval) => i.label ?? 'Busy';

	function describe(p: PlacedInterval): string {
		const what =
			p.interval.source === 'calendar'
				? `${p.interval.ignoreId ? 'Marked not busy, from' : 'Busy from'} ${p.interval.label ?? 'a calendar'}`
				: `Blocked by you${p.interval.label ? `: ${p.interval.label}` : ''}`;
		return `${what}, ${weekdayName(p.day, 'long')} ${clock(p.from)} to ${clock(p.to)}`;
	}
</script>

<div class="dtg">
	<div class="scroller" bind:this={scroller}>
		<div class="body">
			<div class="gutter hours" aria-hidden="true">
				{#each HOURS as h (h)}
					{#if h > 0}<span class="hour" style="top: {top(h * 60)}">{String(h).padStart(2, '0')}:00</span>{/if}
				{/each}
			</div>
			<div
				class="column"
				role="group"
				aria-label="Times on {weekdayName(day, 'long')}. Tap empty time to block it."
				onpointerdown={(e) => down(e, day)}
				onpointermove={move}
				onpointerup={up}
				onpointercancel={cancel}
			>
				{#each timed as p, i (i)}
					{@const minutes = p.to - p.from}
					<button
						type="button"
						class="event {p.interval.source}"
						class:ignored={!!p.interval.ignoreId}
						class:cont-before={p.continuesBefore}
						class:cont-after={p.continuesAfter}
						class:short={minutes < 45}
						style={boxStyle(p)}
						title={describe(p)}
						aria-label="{p.interval.source === 'manual' ? 'Edit: ' : ''}{describe(p)}"
						onclick={() => onopen(p.interval)}
					>
						<span class="elabel">{labelOf(p.interval)}</span>
						<span class="etime">{clock(p.from)} – {clock(p.to)}</span>
					</button>
				{/each}

				{#if selection && selection.day === day}
					<div class="selection" style="top:{top(selection.from)};height:{((selection.to - selection.from) / DAY_MINUTES) * 100}%">
						{clock(selection.from)} – {clock(selection.to)}
					</div>
				{/if}

				{#if nowMinutes !== null && nowMinutes >= 0 && nowMinutes <= DAY_MINUTES}
					<div class="now" style="top: {top(nowMinutes)}" aria-hidden="true"></div>
				{/if}
			</div>
		</div>
	</div>
</div>

<style>
	.dtg {
		--gutter-w: 48px;
		/* At least 48px an hour so every slot is an easy target. */
		--hour: 52px;
		display: flex;
		flex-direction: column;
		min-width: 0;
		min-height: 0;
		height: var(--grid-h, auto);
		user-select: none;
	}
	.scroller {
		height: var(--grid-h, min(60dvh, 620px));
		overflow-y: auto;
		overscroll-behavior: contain;
		padding-top: 8px;
		scrollbar-width: thin;
	}
	.body {
		position: relative;
		display: grid;
		grid-template-columns: var(--gutter-w) minmax(0, 1fr);
		height: calc(var(--hour) * 24);
	}
	.hours {
		position: relative;
	}
	.hour {
		position: absolute;
		right: 8px;
		transform: translateY(-50%);
		font-size: 11px;
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
	.event {
		position: absolute;
		border: 1px solid transparent;
		border-radius: 10px;
		padding: 5px 8px;
		overflow: hidden;
		display: flex;
		flex-direction: column;
		gap: 1px;
		font-size: 13px;
		line-height: 1.25;
		min-height: 18px;
		text-align: left;
		align-items: stretch;
		justify-content: flex-start;
		cursor: pointer;
		transition:
			transform 0.12s ease,
			box-shadow 0.15s ease;
	}
	/* Short items put the time beside the name. */
	.event.short {
		flex-direction: row;
		align-items: center;
		gap: 8px;
		padding-block: 0 1px;
	}
	.event:hover {
		box-shadow: 0 6px 16px -10px var(--shadow);
	}
	.event:active {
		transform: scale(0.98);
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
		backdrop-filter: blur(6px);
	}
	/* Marked not busy: a faded dashed outline. */
	.calendar.ignored {
		background: transparent;
		border: 1px dashed var(--busy-line);
		opacity: 0.6;
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
		font-size: 12px;
		opacity: 0.8;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		font-variant-numeric: tabular-nums;
	}
	.selection {
		position: absolute;
		left: 4px;
		right: 4px;
		border-radius: 10px;
		background: var(--accent-soft);
		border: 1.5px dashed var(--accent);
		color: var(--accent-ink);
		font-size: 12px;
		font-weight: 650;
		padding: 4px 8px;
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
</style>
