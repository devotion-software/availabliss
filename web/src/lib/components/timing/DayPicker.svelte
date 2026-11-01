<script lang="ts">
	import type { Weekday } from '#lib/types';
	import { WEEKDAYS, WEEKDAY_NAMES } from '#lib/client/requirements';

	/** Seven round-rect toggles for the weekdays a slot may fall on. Empty = any day. */
	interface Props {
		days: Weekday[];
		label?: string;
	}
	let { days = $bindable(), label = 'Days it can fall on' }: Props = $props();

	function toggle(d: Weekday) {
		days = days.includes(d) ? days.filter((x) => x !== d) : [...days, d].sort((a, b) => a - b);
	}
</script>

<div class="days" role="group" aria-label={label}>
	{#each WEEKDAYS as d (d)}
		{@const on = days.includes(d)}
		<button type="button" class:on aria-pressed={on} aria-label={WEEKDAY_NAMES[d]} onclick={() => toggle(d)}>
			{WEEKDAY_NAMES[d][0]}
		</button>
	{/each}
</div>

<style>
	.days {
		display: grid;
		grid-template-columns: repeat(7, minmax(0, 1fr));
		gap: 6px;
	}
	button {
		height: 44px;
		border-radius: 14px;
		font-size: 14px;
		font-weight: 700;
		background: var(--surface);
		color: var(--ink);
		border: 1px solid var(--glass-border);
		transition:
			background-color 0.2s ease,
			color 0.2s ease;
	}
	button.on {
		background: var(--accent);
		color: var(--on-accent);
		border-color: var(--accent);
	}
	button:disabled {
		cursor: default;
	}
</style>
