<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLButtonAttributes } from 'svelte/elements';
	import Icon from './Icon.svelte';

	interface Props extends HTMLButtonAttributes {
		/** Toggle chips report their state with aria-pressed. Leave undefined for plain action chips. */
		selected?: boolean;
		/** Colour of the selected state. */
		tone?: 'solid' | 'accent';
		size?: 'sm' | 'md';
		/** Renders a removable tag (accent-soft) with a remove button instead. */
		onremove?: () => void;
		removeLabel?: string;
		dashed?: boolean;
		children: Snippet;
	}
	let {
		selected,
		tone = 'solid',
		size = 'md',
		onremove,
		removeLabel = 'Remove',
		dashed = false,
		children,
		class: className = '',
		...rest
	}: Props = $props();
</script>

{#if onremove}
	<span class="chip tag {size} {className}">
		{@render children()}
		<button type="button" class="remove" aria-label={removeLabel} onclick={onremove}>
			<Icon name="x" size={12} strokeWidth={2.6} />
		</button>
	</span>
{:else}
	<button
		type="button"
		class="chip {size} {tone} {className}"
		class:on={selected}
		class:dashed
		aria-pressed={selected}
		{...rest}
	>
		{@render children()}
	</button>
{/if}

<style>
	.chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		border-radius: var(--radius-pill);
		font-weight: 600;
		white-space: nowrap;
		border: 1px solid var(--glass-border);
		background: var(--surface);
		color: var(--ink);
		transition:
			background-color 0.2s ease,
			color 0.2s ease,
			border-color 0.2s ease;
	}
	.md {
		height: 40px;
		padding: 0 14px;
		font-size: 14px;
	}
	.sm {
		height: 36px;
		padding: 0 12px;
		font-size: 13px;
	}
	button.chip:hover:not(.on) {
		border-color: var(--line-3);
	}
	.solid.on {
		background: var(--solid);
		color: var(--on-solid);
		border-color: var(--solid);
	}
	.accent.on {
		background: var(--accent);
		color: var(--on-accent);
		border-color: var(--accent);
	}
	.dashed {
		background: transparent;
		border: 1px dashed var(--line-3);
	}
	.tag {
		background: var(--accent-soft);
		color: var(--accent-ink);
		border-color: transparent;
		padding-right: 4px;
		height: 36px;
		font-size: 13px;
	}
	.remove {
		width: 28px;
		height: 28px;
		border-radius: 50%;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		color: inherit;
	}
	.remove:hover {
		background: var(--accent-soft);
	}
</style>
