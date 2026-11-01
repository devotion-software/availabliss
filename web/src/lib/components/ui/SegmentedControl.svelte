<script lang="ts" generics="T extends string">
	import Icon, { type IconName } from './Icon.svelte';

	interface Option {
		value: T;
		label: string;
		icon?: IconName;
	}
	interface Props {
		options: readonly Option[];
		value: T;
		/** Accessible name for the group. */
		label: string;
		/** `solid`: selected = solid ink pill on glass (Month/Week/Day). `soft`: raised surface on a sunken track (Mode, Repeat). */
		variant?: 'solid' | 'soft';
		size?: 'sm' | 'md';
		block?: boolean;
		onchange?: (value: T) => void;
	}
	let { options, value = $bindable(), label, variant = 'solid', size = 'md', block = false, onchange }: Props = $props();

	let buttons: HTMLButtonElement[] = $state([]);

	function select(next: T) {
		if (next === value) return;
		value = next;
		onchange?.(next);
	}

	// Radio-group keyboard model: arrows move and select.
	function onkeydown(e: KeyboardEvent, index: number) {
		const step = e.key === 'ArrowRight' || e.key === 'ArrowDown' ? 1 : e.key === 'ArrowLeft' || e.key === 'ArrowUp' ? -1 : 0;
		if (!step) return;
		e.preventDefault();
		const next = (index + step + options.length) % options.length;
		select(options[next].value);
		buttons[next]?.focus();
	}
</script>

<div
	class="seg {variant} {size}"
	class:glass={variant === 'solid'}
	class:block
	role="radiogroup"
	aria-label={label}
	style:--n={options.length}
>
	{#each options as option, i (option.value)}
		{@const on = option.value === value}
		<button
			bind:this={buttons[i]}
			type="button"
			role="radio"
			aria-checked={on}
			tabindex={on ? 0 : -1}
			class:on
			onclick={() => select(option.value)}
			onkeydown={(e) => onkeydown(e, i)}
		>
			{#if option.icon}<Icon name={option.icon} size={16} />{/if}
			{option.label}
		</button>
	{/each}
</div>

<style>
	.seg {
		display: inline-grid;
		grid-template-columns: repeat(var(--n), minmax(0, auto));
		gap: 2px;
		padding: 4px;
		border-radius: 14px;
	}
	.block {
		display: grid;
		grid-template-columns: repeat(var(--n), minmax(0, 1fr));
	}
	button {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: 6px;
		min-width: 52px;
		padding: 0 16px;
		border-radius: 11px;
		font-weight: 600;
		color: var(--ink);
		white-space: nowrap;
		transition:
			background-color 0.2s ease,
			color 0.2s ease,
			box-shadow 0.2s ease;
	}
	.md button {
		height: 36px;
		font-size: 13px;
	}
	.sm button {
		height: 32px;
		min-width: 0;
		padding: 0 10px;
		font-size: 13px;
	}
	.solid button.on {
		background: var(--solid);
		color: var(--on-solid);
	}
	.solid button:not(.on):hover {
		background: var(--surface);
	}
	.soft {
		background: var(--line);
		gap: 6px;
	}
	.soft button {
		color: var(--muted);
	}
	.soft.md button {
		height: 44px;
		font-size: 14px;
		border-radius: 12px;
	}
	.soft button.on {
		background: var(--surface);
		color: var(--ink);
		box-shadow: 0 4px 12px -4px var(--shadow);
	}
	:global([data-scheme='dark']) .soft button.on {
		background: rgba(255, 255, 255, 0.14);
	}
</style>
