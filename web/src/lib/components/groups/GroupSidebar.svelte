<script lang="ts">
	import type { GroupSummary } from '#lib/types';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import { groupStatusText } from './status';

	/** Desktop list of your groups (Web.dc.html), highlighting the open one. */
	interface Props {
		groups: readonly GroupSummary[];
		currentId: string;
	}
	let { groups, currentId }: Props = $props();
</script>

<nav class="sidebar glass" aria-label="Your groups">
	<span class="heading">Your groups</span>
	{#each groups as g (g.id)}
		<a href="/app/groups/{g.id}" aria-current={g.id === currentId ? 'page' : undefined}>
			<GroupTile monogram={g.monogram} color={g.color} size={40} />
			<span class="text">
				<span class="name">{g.name}</span>
				<span class="sub">{groupStatusText(g)}</span>
			</span>
			{#if g.unreadCount && g.id !== currentId}
				<span class="unread" aria-label="{g.unreadCount} unread">{g.unreadCount}</span>
			{/if}
		</a>
	{/each}
</nav>

<style>
	.sidebar {
		border-radius: 28px;
		padding: 12px;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.heading {
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
		padding: 6px 10px;
	}
	a {
		border-radius: 18px;
		padding: 10px;
		display: flex;
		gap: 10px;
		align-items: center;
		text-decoration: none;
		color: var(--ink);
		transition: background-color 0.2s ease;
	}
	a:hover,
	a[aria-current='page'] {
		background: var(--surface);
		color: var(--ink);
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.name {
		font-size: 14px;
		font-weight: 600;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.unread {
		min-width: 20px;
		height: 20px;
		padding: 0 6px;
		border-radius: 10px;
		background: var(--warn);
		color: #1a1b3a;
		font-size: 11px;
		font-weight: 700;
		line-height: 20px;
		text-align: center;
	}
</style>
