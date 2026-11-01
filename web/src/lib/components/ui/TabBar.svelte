<script lang="ts">
	import Icon, { type IconName } from './Icon.svelte';

	export interface Tab {
		href: string;
		label: string;
		icon: IconName;
		current: boolean;
		/** Count badge, or `true` for a dot. */
		badge?: number | boolean;
	}
	interface Props {
		tabs: readonly Tab[];
		label: string;
	}
	let { tabs, label }: Props = $props();
</script>

<nav class="tabbar glass" aria-label={label} style:--n={tabs.length}>
	{#each tabs as tab (tab.href)}
		<a href={tab.href} aria-current={tab.current ? 'page' : undefined} class:current={tab.current}>
			<span class="icon">
				<Icon name={tab.icon} size={22} />
				{#if tab.badge === true}
					<span class="dot" aria-label="New"></span>
				{:else if tab.badge}
					<span class="count" aria-label="{tab.badge} new">{tab.badge > 99 ? '99+' : tab.badge}</span>
				{/if}
			</span>
			{tab.label}
		</a>
	{/each}
</nav>

<style>
	.tabbar {
		position: fixed;
		left: 20px;
		right: 20px;
		bottom: max(22px, env(safe-area-inset-bottom));
		z-index: 50;
		height: 66px;
		border-radius: 24px;
		display: grid;
		grid-template-columns: repeat(var(--n), minmax(0, 1fr));
		padding: 0 6px;
		max-width: 520px;
		margin: 0 auto;
	}
	a {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 3px;
		font-size: 11px;
		font-weight: 500;
		text-decoration: none;
		color: var(--muted);
		border-radius: 18px;
	}
	a:hover {
		color: var(--ink);
	}
	a.current {
		color: var(--accent-ink);
		font-weight: 600;
	}
	.icon {
		position: relative;
	}
	.dot {
		position: absolute;
		top: -1px;
		right: -4px;
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: var(--warn);
		border: 1.5px solid var(--bg);
	}
	.count {
		position: absolute;
		top: -6px;
		left: calc(100% - 7px);
		min-width: 16px;
		height: 16px;
		padding: 0 4px;
		border-radius: 8px;
		background: var(--warn);
		color: #1a1b3a;
		font-size: 10px;
		font-weight: 700;
		line-height: 16px;
		text-align: center;
	}
	@media (min-width: 900px) {
		.tabbar {
			display: none;
		}
	}
</style>
