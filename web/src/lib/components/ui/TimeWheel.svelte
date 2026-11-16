<script lang="ts">
	/** An inline time picker: an hours column and a minutes column (5-minute steps), like a wheel you tap. */
	interface Props {
		value: string;
		label: string;
		onpick: (time: string) => void;
	}
	let { value, label, onpick }: Props = $props();

	const pad = (n: number) => String(n).padStart(2, '0');
	const hour = $derived(Number(value.slice(0, 2)) || 0);
	const minute = $derived(Number(value.slice(3, 5)) || 0);
	const HOURS = Array.from({ length: 24 }, (_, i) => i);
	// Every 5 minutes, plus the current minute if it's off the grid (e.g. 23:59).
	const minutes = $derived([...new Set([...Array.from({ length: 12 }, (_, i) => i * 5), minute])].sort((a, b) => a - b));

	let hoursEl: HTMLElement | undefined = $state();
	let minutesEl: HTMLElement | undefined = $state();

	// Centre the chosen values when the picker opens.
	$effect(() => {
		for (const el of [hoursEl, minutesEl]) {
			const sel = el?.querySelector<HTMLElement>('[aria-pressed="true"]');
			if (el && sel) el.scrollTop = sel.offsetTop - el.clientHeight / 2 + sel.offsetHeight / 2;
		}
	});
</script>

<div class="wheel" role="group" aria-label={label}>
	<div class="col" bind:this={hoursEl} role="group" aria-label="Hour">
		{#each HOURS as h (h)}
			<button type="button" class:sel={h === hour} aria-pressed={h === hour} aria-label="{pad(h)} hours" onclick={() => onpick(`${pad(h)}:${pad(minute)}`)}>{pad(h)}</button>
		{/each}
	</div>
	<span class="colon" aria-hidden="true">:</span>
	<div class="col" bind:this={minutesEl} role="group" aria-label="Minutes">
		{#each minutes as m (m)}
			<button type="button" class:sel={m === minute} aria-pressed={m === minute} aria-label="{pad(m)} minutes" onclick={() => onpick(`${pad(hour)}:${pad(m)}`)}>{pad(m)}</button>
		{/each}
	</div>
</div>

<style>
	.wheel {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 6px;
	}
	.col {
		position: relative;
		width: 84px;
		height: 200px;
		overflow-y: auto;
		scroll-snap-type: y proximity;
		display: flex;
		flex-direction: column;
		gap: 2px;
		padding: 4px;
		border-radius: 16px;
		background: var(--surface);
		border: 1px solid var(--line-2);
		scrollbar-width: none;
	}
	.col::-webkit-scrollbar {
		display: none;
	}
	.colon {
		font-size: 22px;
		font-weight: 700;
		color: var(--muted);
	}
	button {
		flex: none;
		height: 44px;
		border-radius: 12px;
		font-size: 20px;
		font-weight: 700;
		font-variant-numeric: tabular-nums;
		color: var(--ink);
		scroll-snap-align: center;
	}
	button:hover:not(.sel) {
		background: var(--line);
	}
	button.sel {
		background: var(--accent);
		color: var(--on-accent);
	}
	button:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: -2px;
	}
</style>
