<script lang="ts">
	import { addDays, addMonths, endOfMonth, parseKey, startOfMonth, weekdayOf } from '#lib/client/dates';
	import { formatDayRange, monthTitle } from '#lib/client/format';
	import IconButton from './IconButton.svelte';
	import { spokenDate } from './date-range';

	/**
	 * One month calendar for picking a range of days: tap the first day, then the last, and the
	 * days between are highlighted. A third tap starts a new range. One day on its own is a range
	 * too, so `from` and `to` are both set from the first tap ('' = nothing picked yet).
	 */
	interface Props {
		from: string;
		to: string;
		/** Earliest day that can be picked (inclusive). */
		min?: string | null;
		/** The month to show first when nothing is picked (default: this month). */
		opensAt?: string | null;
		label: string;
	}
	let { from = $bindable(), to = $bindable(), min = null, opensAt = null, label }: Props = $props();

	const HEAD = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
	const HEAD_LONG = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

	const now = new Date();
	const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;

	/** The first day tapped, while waiting for the last. */
	let anchor = $state<string | null>(null);
	// svelte-ignore state_referenced_locally
	let month = $state(startOfMonth(from || opensAt || (min && min > today ? min : today)));

	const cells = $derived.by(() => {
		const out: (string | null)[] = Array.from({ length: weekdayOf(month) }, () => null);
		for (let d = month, last = endOfMonth(month); d <= last; d = addDays(d, 1)) out.push(d);
		while (out.length < 42) out.push(null);
		return out;
	});
	const canPrev = $derived(!min || addDays(month, -1) >= min);
	const dayNum = (d: string) => parseKey(d).getUTCDate();
	const inRange = (d: string) => !!from && !!to && from <= d && d <= to;
	const hint = $derived(anchor ? 'Now pick the last day.' : from && to ? formatDayRange(from, to) : 'Pick the first day, then the last.');

	function pick(d: string) {
		if (anchor) {
			from = d < anchor ? d : anchor;
			to = d < anchor ? anchor : d;
			anchor = null;
		} else {
			from = to = anchor = d;
		}
	}
</script>

<div class="cal" role="group" aria-label={label}>
	<div class="head">
		<IconButton
			icon="chevron-left"
			label="Previous month"
			variant="plain"
			size={40}
			disabled={!canPrev}
			onclick={() => (month = addMonths(month, -1))}
		/>
		<span class="title" aria-live="polite">{monthTitle(month)}</span>
		<IconButton icon="chevron-right" label="Next month" variant="plain" size={40} onclick={() => (month = addMonths(month, 1))} />
	</div>
	<div class="grid">
		{#each HEAD as h, i (i)}<abbr class="dow" title={HEAD_LONG[i]}>{h}</abbr>{/each}
		{#each cells as d, i (d ?? `blank-${i}`)}
			{#if d}
				{@const start = d === from}
				{@const end = d === to}
				<div
					class="cell"
					class:band={inRange(d) && from !== to}
					class:first={start}
					class:last={end}
					class:wk-start={i % 7 === 0}
					class:wk-end={i % 7 === 6}
				>
					<button
						type="button"
						class="day"
						class:sel={start || end}
						class:mid={inRange(d) && !start && !end}
						class:today={d === today}
						disabled={!!min && d < min}
						aria-pressed={inRange(d)}
						aria-label={spokenDate(d) + (start && end ? '' : start ? ', first day' : end ? ', last day' : '')}
						onclick={() => pick(d)}
					>
						{dayNum(d)}
					</button>
				</div>
			{:else}
				<span class="cell"></span>
			{/if}
		{/each}
	</div>
	<p class="hint" aria-live="polite">{hint}</p>
</div>

<style>
	.cal {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.title {
		font-family: var(--font-display);
		font-size: 16px;
		font-weight: 700;
	}
	.grid {
		display: grid;
		grid-template-columns: repeat(7, minmax(0, 1fr));
		row-gap: 2px;
	}
	.dow {
		font-size: 11px;
		font-weight: 700;
		color: var(--muted);
		text-decoration: none;
		text-align: center;
		padding: 4px 0;
	}
	.cell {
		display: flex;
		justify-content: center;
		height: 40px;
		position: relative;
	}
	/* The soft band joining the days in the range: half-width under the first and last, rounded at week edges. */
	.cell.band::before {
		content: '';
		position: absolute;
		inset: 0;
		background: var(--accent-soft);
	}
	.cell.band.first::before {
		left: 50%;
	}
	.cell.band.last::before {
		right: 50%;
	}
	.cell.band.wk-start:not(.first)::before {
		border-radius: 20px 0 0 20px;
	}
	.cell.band.wk-end:not(.last)::before {
		border-radius: 0 20px 20px 0;
	}
	.day {
		position: relative;
		width: 40px;
		height: 40px;
		border-radius: 999px;
		font-size: 15px;
		font-weight: 600;
		font-variant-numeric: tabular-nums;
		color: var(--ink);
	}
	.day:hover:not(:disabled):not(.sel) {
		background: var(--line);
	}
	.day.today {
		box-shadow: inset 0 0 0 1.5px var(--line-3);
	}
	.day.mid {
		color: var(--accent-ink);
	}
	.day.sel {
		background: var(--accent);
		color: var(--on-accent);
		box-shadow: none;
	}
	.day:disabled {
		color: var(--line-3);
		cursor: default;
	}
	.day:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: 1px;
	}
	.hint {
		font-size: 13px;
		color: var(--muted);
		margin-top: 4px;
	}
</style>
