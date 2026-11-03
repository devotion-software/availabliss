<script lang="ts">
	import { page } from '$app/state';
	import { invalidate } from '$app/navigation';
	import { live } from '#lib/client/live.svelte';
	import { unread } from '#lib/client/unread.svelte';
	import TabBar from '#lib/components/ui/TabBar.svelte';
	import GroupSidebar from '#lib/components/groups/GroupSidebar.svelte';

	let { data, children } = $props();

	const base = $derived(`/app/groups/${data.group.id}`);
	const route = $derived(page.route.id ?? '');
	const unreadChat = $derived(unread.count(data.groups.find((g) => g.id === data.group.id) ?? { id: data.group.id }));

	// Screens with their own bottom bar (chat composer, timing save bar, editors) skip the tab bar.
	const showTabs = $derived(
		['/app/groups/[id]', '/app/groups/[id]/proposals', '/app/groups/[id]/members'].includes(route)
	);
	const tabs = $derived([
		{ href: base, label: 'Calendar', icon: 'calendar' as const, current: route === '/app/groups/[id]' },
		{ href: `${base}/chat`, label: 'Chat', icon: 'chat' as const, current: route.endsWith('/chat'), badge: unreadChat },
		{ href: `${base}/proposals`, label: 'Proposals', icon: 'check-circle' as const, current: route.endsWith('/proposals') },
		{ href: `${base}/timing`, label: 'Timing', icon: 'sliders' as const, current: route.endsWith('/timing') }
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

<div class="group-layout" class:with-tabs={showTabs}>
	<aside class="side">
		<GroupSidebar groups={data.groups} currentId={data.group.id} />
	</aside>
	<div class="content">
		{@render children()}
	</div>
</div>
{#if showTabs}
	<TabBar label="Group" {tabs} />
{/if}

<style>
	.group-layout {
		flex: 1;
		display: flex;
		gap: 20px;
		align-items: flex-start;
		min-width: 0;
	}
	.with-tabs {
		padding-bottom: calc(var(--tabbar-space) + env(safe-area-inset-bottom) - 32px);
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
		.with-tabs {
			padding-bottom: 0;
		}
		.side {
			display: block;
			flex: 0 0 clamp(240px, 20vw, 300px);
			position: sticky;
			top: 20px;
		}
	}
</style>
