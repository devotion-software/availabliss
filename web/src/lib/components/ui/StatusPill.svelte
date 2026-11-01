<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon, { type IconName } from './Icon.svelte';

	export type PillTone = 'indigo' | 'mint' | 'apricot' | 'neutral';
	interface Props {
		tone?: PillTone;
		icon?: IconName;
		size?: 'sm' | 'md';
		children: Snippet;
	}
	let { tone = 'neutral', icon, size = 'sm', children }: Props = $props();
</script>

<span class="pill {tone} {size}">
	{#if icon}<Icon name={icon} size={12} strokeWidth={2.8} />{/if}
	{@render children()}
</span>

<style>
	.pill {
		flex: none;
		display: inline-flex;
		align-items: center;
		gap: 5px;
		border-radius: var(--radius-pill);
		font-weight: 600;
		white-space: nowrap;
		line-height: 1.2;
	}
	.sm {
		font-size: 11px;
		padding: 4px 8px;
	}
	.md {
		font-size: 12px;
		padding: 5px 10px;
	}
	.indigo {
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	.mint {
		background: var(--ok-bg);
		color: var(--ok-ink);
	}
	.apricot {
		background: var(--warn-bg);
		color: var(--warn-ink);
	}
	.neutral {
		background: var(--line);
		color: var(--neutral-ink);
	}
</style>
