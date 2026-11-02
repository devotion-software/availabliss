<script lang="ts">
	import type { Snippet } from 'svelte';
	import IconButton from './IconButton.svelte';
	import type { IconName } from './Icon.svelte';

	/** Phone-style screen header: round back button, title + subtitle, trailing actions. */
	interface Props {
		title: string;
		subtitle?: string;
		backHref?: string;
		backLabel?: string;
		/** `home` for top-level screens whose way out is the groups home rather than "back". */
		backIcon?: Extract<IconName, 'chevron-left' | 'home'>;
		/** Hide the back button from wide layouts where navigation is always visible. */
		backOnPhoneOnly?: boolean;
		size?: 'md' | 'lg';
		actions?: Snippet;
		after?: Snippet;
	}
	let { title, subtitle, backHref, backLabel = 'Back', backIcon = 'chevron-left', backOnPhoneOnly = false, size = 'md', actions, after }: Props = $props();
</script>

<header class="page-header {size}">
	{#if backHref}
		<span class:phone-only={backOnPhoneOnly}>
			<IconButton href={backHref} icon={backIcon} label={backLabel} />
		</span>
	{/if}
	<div class="titles">
		<h1>{title}</h1>
		{#if subtitle}<span class="sub">{subtitle}</span>{/if}
		{#if after}{@render after()}{/if}
	</div>
	{#if actions}<div class="actions">{@render actions()}</div>{/if}
</header>

<style>
	.page-header {
		display: flex;
		align-items: center;
		gap: 10px;
		min-width: 0;
	}
	.titles {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	h1 {
		font-size: 24px;
		font-weight: 700;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.md h1 {
		font-size: 22px;
	}
	.lg h1 {
		font-size: 24px;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
	}
	.actions {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	@media (min-width: 900px) {
		h1 {
			font-size: 30px !important;
			font-weight: 750;
		}
		.sub {
			font-size: 14px;
		}
		.phone-only {
			display: none;
		}
	}
</style>
