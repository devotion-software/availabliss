<script lang="ts" module>
	/** "Groups › Lake District crew › Timing". The last item is the current page. */
	export interface Crumb {
		label: string;
		href?: string;
	}
</script>

<script lang="ts">
	import Icon from './Icon.svelte';

	interface Props {
		items: Crumb[];
	}
	let { items }: Props = $props();
</script>

{#if items.length}
	<nav class="crumbs" aria-label="Breadcrumb">
		<ol>
			{#each items as item, i (i)}
				{@const last = i === items.length - 1}
				<li>
					{#if item.href && !last}
						<a href={item.href}>{item.label}</a>
					{:else}
						<span aria-current={last ? 'page' : undefined}>{item.label}</span>
					{/if}
					{#if !last}<Icon name="chevron-right" size={12} />{/if}
				</li>
			{/each}
		</ol>
	</nav>
{/if}

<style>
	ol {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 4px;
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
	}
	li {
		display: flex;
		align-items: center;
		gap: 4px;
		min-width: 0;
	}
	a,
	span {
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		max-width: 22ch;
	}
	a {
		color: var(--muted);
		text-decoration: none;
		border-radius: 6px;
		padding: 2px 4px;
		margin: -2px -4px;
	}
	a:hover {
		color: var(--accent-ink);
		background: var(--accent-soft);
	}
	span[aria-current='page'] {
		color: var(--ink);
	}
	li :global(svg) {
		opacity: 0.6;
		flex: none;
	}
</style>
