<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLAnchorAttributes, HTMLButtonAttributes } from 'svelte/elements';
	import Icon, { type IconName } from './Icon.svelte';
	import Spinner from './Spinner.svelte';

	type Variant = 'solid' | 'accent' | 'secondary' | 'ghost' | 'danger' | 'warn' | 'glass';
	type Size = 'sm' | 'md' | 'lg';

	type Common = {
		variant?: Variant;
		size?: Size;
		icon?: IconName;
		iconAfter?: IconName;
		loading?: boolean;
		/** Stretch to the container's width. */
		block?: boolean;
		children?: Snippet;
	};
	type Props = Common &
		(({ href: string } & Omit<HTMLAnchorAttributes, 'href'>) | ({ href?: undefined } & HTMLButtonAttributes));

	let {
		variant = 'solid',
		size = 'md',
		icon,
		iconAfter,
		loading = false,
		block = false,
		children,
		class: className = '',
		...rest
	}: Props = $props();

	const iconSize = $derived(size === 'sm' ? 15 : 18);
	const classes = $derived(['btn', variant, size, block && 'block', className].filter(Boolean).join(' '));
</script>

{#snippet content()}
	{#if loading}
		<Spinner size={iconSize - 2} />
	{:else if icon}
		<Icon name={icon} size={iconSize} strokeWidth={2.2} />
	{/if}
	{#if children}<span class="label">{@render children()}</span>{/if}
	{#if iconAfter && !loading}<Icon name={iconAfter} size={iconSize} strokeWidth={2.2} />{/if}
{/snippet}

{#if rest.href !== undefined}
	<a class={classes} {...rest as HTMLAnchorAttributes}>{@render content()}</a>
{:else}
	{@const buttonProps = rest as HTMLButtonAttributes}
	<button
		type="button"
		{...buttonProps}
		class={classes}
		disabled={buttonProps.disabled || loading}
		aria-busy={loading || undefined}
	>
		{@render content()}
	</button>
{/if}

<style>
	.btn {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		border: 1px solid transparent;
		font-weight: 600;
		text-decoration: none;
		white-space: nowrap;
		user-select: none;
		transition:
			transform 0.15s var(--ease-out),
			background-color 0.2s ease,
			box-shadow 0.2s ease,
			opacity 0.2s ease;
	}
	.btn:active:not(:disabled) {
		transform: scale(0.97);
	}
	.btn:disabled {
		opacity: 0.55;
	}
	.label {
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.sm {
		height: 38px;
		padding: 0 14px;
		border-radius: 12px;
		font-size: 13px;
	}
	.md {
		height: 50px;
		padding: 0 20px;
		border-radius: 16px;
		font-size: 15px;
	}
	.lg {
		height: 54px;
		padding: 0 24px;
		border-radius: 18px;
		font-size: 16px;
	}
	.block {
		display: flex;
		width: 100%;
	}

	.solid {
		background: var(--solid);
		color: var(--on-solid);
	}
	.solid:hover:not(:disabled) {
		color: var(--on-solid);
		box-shadow: 0 12px 24px -16px var(--shadow);
	}
	.accent {
		background: var(--accent);
		color: var(--on-accent);
	}
	.accent:hover:not(:disabled) {
		color: var(--on-accent);
		box-shadow: 0 12px 24px -14px var(--accent);
	}
	.secondary {
		background: var(--surface);
		border-color: var(--line-2);
		color: var(--ink);
	}
	.secondary:hover:not(:disabled) {
		color: var(--ink);
		border-color: var(--line-3);
	}
	.glass {
		background: var(--glass);
		-webkit-backdrop-filter: var(--blur);
		backdrop-filter: var(--blur);
		border-color: var(--glass-border);
		box-shadow: var(--glass-shadow);
		color: var(--ink);
	}
	.glass:hover:not(:disabled) {
		color: var(--ink);
	}
	.ghost {
		background: transparent;
		color: var(--accent-ink);
	}
	.ghost:hover:not(:disabled) {
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	.warn {
		background: var(--warn-bg);
		color: var(--warn-ink);
	}
	.warn:hover:not(:disabled) {
		color: var(--warn-ink);
	}
	.danger {
		background: var(--warn-bg);
		color: var(--warn-ink);
		border-color: color-mix(in srgb, var(--warn) 40%, transparent);
	}
	.danger:hover:not(:disabled) {
		color: var(--warn-ink);
		border-color: var(--warn);
	}
</style>
