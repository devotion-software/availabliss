<script lang="ts">
	import { page } from '$app/state';
	import { goto, invalidate } from '$app/navigation';
	import { live } from '#lib/client/live.svelte';
	import { unread } from '#lib/client/unread.svelte';
	import GroupSidebar from '#lib/components/groups/GroupSidebar.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import Menu from '#lib/components/ui/Menu.svelte';
	import { groupSubtitle, isAdminRole } from '#lib/client/members';

	let { data, children } = $props();

	const base = $derived(`/app/groups/${data.group.id}`);
	const route = $derived(page.route.id ?? '');
	const unreadChat = $derived(unread.count(data.groups.find((g) => g.id === data.group.id) ?? { id: data.group.id }));

	// Phones: the calendar is the group's home, with its header pinned at the top. Chat, Plans,
	// Timing and Members open as their own screens, each with a way back to the calendar.
	const showNav = $derived(route === '/app/groups/[id]');
	const isAdmin = $derived(isAdminRole(data.group.role));
	const more = $derived([
		{ label: 'Plans', icon: 'check-circle' as const, onselect: () => goto(`${base}/proposals`) },
		{ label: 'Timing', icon: 'sliders' as const, onselect: () => goto(`${base}/timing`) },
		{
			label: isAdmin ? 'Members and invites' : 'Members',
			icon: isAdmin ? ('user-plus' as const) : ('users' as const),
			onselect: () => goto(`${base}/members`)
		}
	]);

	// Group details (name, members, roles, timing) change under us; refetch when they do.
	$effect(() => {
		const id = data.group.id;
		const off = live.on(['group.updated', 'member.changed'], (e) => {
			if (e.groupId === id) invalidate('app:group');
		});
		const offReconnect = live.onReconnect(() => invalidate('app:group'));
		return () => {
			off();
			offReconnect();
		};
	});
</script>

<div class="group-layout">
	<aside class="side">
		<GroupSidebar groups={data.groups} currentId={data.group.id} />
	</aside>
	<div class="content">
		{#if showNav}
			<header class="phone-nav">
				<IconButton href="/app" icon="chevron-left" label="Back to groups" />
				<div class="titles">
					<h1>{data.group.name}</h1>
					<span class="sub">{groupSubtitle(data.group)}</span>
				</div>
				<IconButton href="{base}/chat" icon="chat" label={unreadChat ? `Chat, ${unreadChat} unread` : 'Chat'} badge={unreadChat} />
				<Menu icon="more" label="More" items={more} />
			</header>
		{/if}
		{@render children()}
	</div>
</div>

<style>
	.group-layout {
		flex: 1;
		display: flex;
		gap: 20px;
		align-items: flex-start;
		min-width: 0;
	}
	.phone-nav {
		position: sticky;
		top: 0;
		z-index: 20;
		display: flex;
		align-items: center;
		gap: 10px;
		margin: calc(-1 * max(20px, env(safe-area-inset-top))) calc(-1 * var(--gutter)) 12px;
		padding: max(12px, env(safe-area-inset-top)) var(--gutter) 10px;
		background: linear-gradient(var(--bg) 75%, transparent);
	}
	.titles {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.phone-nav h1 {
		font-size: 22px;
		font-weight: 700;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
	}
	.side {
		display: none;
	}
	.content {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	@media (min-width: 900px) {
		.phone-nav {
			display: none;
		}
		.side {
			display: block;
			flex: 0 0 clamp(240px, 20vw, 300px);
			position: sticky;
			top: 20px;
		}
	}
</style>
