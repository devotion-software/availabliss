<script lang="ts">
	import type { HTMLAnchorAttributes, HTMLButtonAttributes } from 'svelte/elements';
	import Icon, { type IconName } from './Icon.svelte';

	type Common = {
		icon: IconName;
		/** Required: icon buttons have no visible text. */
		label: string;
		variant?: 'glass' | 'surface' | 'plain' | 'solid' | 'accent' | 'line';
		/** 44 = round touch target; 40 = squarer nav buttons (still ≥ 40 with padding hit area). */
		size?: 36 | 40 | 44;
		shape?: 'circle' | 'rounded';
		iconSize?: number;
		badge?: number | boolean;
	};
	type Props = Common &
		(({ href: string } & Omit<HTMLAnchorAttributes, 'href'>) | ({ href?: undefined } & HTMLButtonAttributes));

	let {
		icon,
		label,
		variant = 'glass',
		size = 44,
		shape = 'circle',
		iconSize,
		badge,
		class: className = '',
		...rest
	}: Props = $props();

	const classes = $derived(`icon-btn ${variant} ${shape} ${className}`);
	const badgeText = $derived(typeof badge === 'number' ? (badge > 99 ? '99+' : String(badge)) : '');
	const showBadge = $derived(badge === true || (typeof badge === 'number' && badge > 0));
</script>

{#snippet inner()}
	<Icon name={icon} size={iconSize ?? (size === 44 ? 20 : 16)} strokeWidth={2.2} />
	{#if showBadge}
		<span class="badge" class:dot={badge === true} aria-hidden="true">{badgeText}</span>
	{/if}
{/snippet}

{#if rest.href !== undefined}
	<a class={classes} style:--s="{size}px" aria-label={label} title={label} {...rest as HTMLAnchorAttributes}>{@render inner()}</a>
{:else}
	<button type="button" class={classes} style:--s="{size}px" aria-label={label} title={label} {...rest as HTMLButtonAttributes}>
		{@render inner()}
	</button>
{/if}

<style>
	.icon-btn {
		position: relative;
		flex: none;
		width: var(--s);
		height: var(--s);
		display: inline-flex;
		align-items: center;
		justify-content: center;
		color: var(--ink);
		border: 1px solid transparent;
		transition:
			transform 0.15s var(--ease-out),
			background-color 0.2s ease;
	}
	.icon-btn:hover {
		color: var(--ink);
	}
	.icon-btn:active:not(:disabled) {
		transform: scale(0.94);
	}
	.icon-btn:disabled {
		opacity: 0.5;
	}
	.circle {
		border-radius: 50%;
	}
	.rounded {
		border-radius: 12px;
	}
	.glass {
		background: var(--glass);
		-webkit-backdrop-filter: var(--blur);
		backdrop-filter: var(--blur);
		border-color: var(--glass-border);
		box-shadow: var(--glass-shadow);
	}
	.surface {
		background: var(--surface);
	}
	.line {
		background: var(--line);
	}
	.plain:hover {
		background: var(--surface);
	}
	.solid {
		background: var(--solid);
		color: var(--on-solid);
	}
	.solid:hover {
		color: var(--on-solid);
	}
	.accent {
		background: var(--accent);
		color: var(--on-accent);
	}
	.accent:hover {
		color: var(--on-accent);
	}
	.badge {
		position: absolute;
		top: -2px;
		right: -2px;
		min-width: 18px;
		height: 18px;
		padding: 0 5px;
		border-radius: 9px;
		background: var(--warn);
		color: #1a1b3a;
		font-size: 10px;
		font-weight: 700;
		line-height: 18px;
		text-align: center;
		border: 1.5px solid var(--bg);
	}
	.badge.dot {
		min-width: 10px;
		width: 10px;
		height: 10px;
		padding: 0;
		top: 2px;
		right: 2px;
	}
</style>
