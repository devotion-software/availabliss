<script lang="ts">
	import type { Snippet } from 'svelte';
	import Logo from './Logo.svelte';
	import Icon, { type IconName } from './Icon.svelte';

	interface Props {
		title: string;
		message?: string;
		/** Line icon; defaults to the Venn mark. */
		icon?: IconName;
		compact?: boolean;
		actions?: Snippet;
	}
	let { title, message, icon, compact = false, actions }: Props = $props();
</script>

<div class="empty" class:compact>
	<span class="mark">
		{#if icon}<Icon name={icon} size={compact ? 22 : 26} />{:else}<Logo size={compact ? 40 : 56} />{/if}
	</span>
	<h3>{title}</h3>
	{#if message}<p>{message}</p>{/if}
	{#if actions}<div class="actions">{@render actions()}</div>{/if}
</div>

<style>
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
		gap: 8px;
		padding: 32px 20px;
	}
	.compact {
		padding: 18px 12px;
	}
	.mark {
		display: flex;
		align-items: center;
		justify-content: center;
		margin-bottom: 6px;
		color: var(--accent-ink);
	}
	.mark:has(:global(svg:not(.venn))) {
		width: 56px;
		height: 56px;
		border-radius: 18px;
		background: var(--accent-soft);
	}
	h3 {
		font-size: 19px;
		font-weight: 650;
	}
	.compact h3 {
		font-size: 16px;
	}
	p {
		max-width: 34ch;
		font-size: 14px;
		color: var(--muted);
	}
	.actions {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 8px;
		margin-top: 8px;
	}
</style>
