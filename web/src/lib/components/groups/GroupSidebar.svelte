<script lang="ts">
	import type { GroupSummary } from '#lib/types';
	import { page } from '$app/state';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import Icon, { type IconName } from '#lib/components/ui/Icon.svelte';
	import { groupStatusText } from './status';

	/** Desktop list of your groups (Web.dc.html). The open group expands to show its sections. */
	interface Props {
		groups: readonly GroupSummary[];
		currentId: string;
	}
	let { groups, currentId }: Props = $props();

	const route = $derived(page.route.id ?? '');
	const SECTIONS: { path: string; label: string; icon: IconName; routes: string[] }[] = [
		{ path: '', label: 'Calendar', icon: 'calendar', routes: ['/app/groups/[id]', '/app/groups/[id]/events/new'] },
		{ path: '/chat', label: 'Chat', icon: 'chat', routes: ['/app/groups/[id]/chat'] },
		{ path: '/proposals', label: 'Proposals', icon: 'check-circle', routes: ['/app/groups/[id]/proposals', '/app/proposals/[id]'] },
		{ path: '/timing', label: 'Timing', icon: 'sliders', routes: ['/app/groups/[id]/timing'] },
		{ path: '/members', label: 'Members', icon: 'user-plus', routes: ['/app/groups/[id]/members'] }
	];
</script>

<nav class="sidebar glass" aria-label="Your groups">
	<span class="heading">Your groups</span>
	{#each groups as g (g.id)}
		<a href="/app/groups/{g.id}" class="group" class:open={g.id === currentId} aria-current={g.id === currentId && route === '/app/groups/[id]' ? 'page' : undefined}>
			<GroupTile monogram={g.monogram} color={g.color} size={40} />
			<span class="text">
				<span class="name">{g.name}</span>
				<span class="sub">{groupStatusText(g)}</span>
			</span>
			{#if g.unreadCount && g.id !== currentId}
				<span class="unread" aria-label="{g.unreadCount} unread">{g.unreadCount}</span>
			{/if}
		</a>
		{#if g.id === currentId}
			<ul class="sections" aria-label="{g.name} sections">
				{#each SECTIONS as sec (sec.path)}
					{@const unread = sec.path === '/chat' ? (g.unreadCount ?? 0) : 0}
					<li>
						<a href="/app/groups/{g.id}{sec.path}" aria-current={sec.routes.includes(route) ? 'page' : undefined}>
							<Icon name={sec.icon} size={16} />
							<span>{sec.label}</span>
							{#if unread}<span class="unread" aria-label="{unread} unread">{unread}</span>{/if}
						</a>
					</li>
				{/each}
			</ul>
		{/if}
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
	.group.open {
		background: var(--surface);
		color: var(--ink);
	}
	.sections {
		list-style: none;
		margin: 2px 0 6px;
		padding: 0 0 0 30px;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.sections a {
		padding: 7px 10px;
		border-radius: 12px;
		font-size: 13px;
		font-weight: 600;
		color: var(--muted);
		gap: 8px;
	}
	.sections a span:first-of-type {
		flex: 1;
	}
	.sections a:hover {
		color: var(--ink);
	}
	.sections a[aria-current='page'] {
		color: var(--accent-ink);
		background: var(--accent-soft);
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
