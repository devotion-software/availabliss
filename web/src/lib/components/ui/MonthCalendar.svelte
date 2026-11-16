<script lang="ts">
	import { addDays, addMonths, endOfMonth, parseKey, startOfMonth, weekdayOf } from '#lib/client/dates';
	import { monthTitle } from '#lib/client/format';
	import IconButton from './IconButton.svelte';
	import { spokenDate } from './date-range';

	/** A small inline month calendar for picking one date (the date range input's date picker). */
	interface Props {
		value: string;
		/** Earliest / latest date that can be picked (inclusive). */
		min?: string | null;
		max?: string | null;
		/** Today, marked with a ring. */
		today?: string | null;
		label: string;
		onpick: (date: string) => void;
	}
	let { value, min = null, max = null, today = null, label, onpick }: Props = $props();

	const HEAD = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
	const HEAD_LONG = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

	// svelte-ignore state_referenced_locally
	let month = $state(startOfMonth(value || today || '2000-01-01'));
	const cells = $derived.by(() => {
		const out: (string | null)[] = Array.from({ length: weekdayOf(month) }, () => null);
		for (let d = month, last = endOfMonth(month); d <= last; d = addDays(d, 1)) out.push(d);
		return out;
	});
	const disabled = (d: string) => (!!min && d < min) || (!!max && d > max);
	const canPrev = $derived(!min || addDays(month, -1) >= min);
	const canNext = $derived(!max || addMonths(month, 1) <= max);
	const dayNum = (d: string) => parseKey(d).getUTCDate();
</script>

<div class="cal" role="group" aria-label={label}>
	<div class="head">
		<IconButton icon="chevron-left" label="Previous month" variant="plain" size={40} disabled={!canPrev} onclick={() => (month = addMonths(month, -1))} />
		<span class="title" aria-live="polite">{monthTitle(month)}</span>
		<IconButton icon="chevron-right" label="Next month" variant="plain" size={40} disabled={!canNext} onclick={() => (month = addMonths(month, 1))} />
	</div>
	<div class="grid">
		{#each HEAD as h, i (i)}<abbr class="dow" title={HEAD_LONG[i]}>{h}</abbr>{/each}
		{#each cells as d, i (d ?? `blank-${i}`)}
			{#if d}
				<button
					type="button"
					class="day"
					class:sel={d === value}
					class:today={d === today}
					disabled={disabled(d)}
					aria-pressed={d === value}
					aria-label={spokenDate(d)}
					onclick={() => onpick(d)}>{dayNum(d)}</button
				>
			{:else}
				<span></span>
			{/if}
		{/each}
	</div>
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
		gap: 2px;
		justify-items: center;
	}
	.dow {
		font-size: 11px;
		font-weight: 700;
		color: var(--muted);
		text-decoration: none;
		padding: 4px 0;
	}
	.day {
		width: 100%;
		max-width: 44px;
		height: 40px;
		border-radius: 12px;
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
</style>
