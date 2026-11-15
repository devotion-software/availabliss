<script lang="ts">
	import { page } from '$app/state';
	import { invalidate } from '$app/navigation';
	import { live } from '#lib/client/live.svelte';
	import { unread } from '#lib/client/unread.svelte';
	import GroupSidebar from '#lib/components/groups/GroupSidebar.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { groupSubtitle, isAdminRole } from '#lib/client/members';

	let { data, children } = $props();

	const base = $derived(`/app/groups/${data.group.id}`);
	const route = $derived(page.route.id ?? '');
	const unreadChat = $derived(unread.count(data.groups.find((g) => g.id === data.group.id) ?? { id: data.group.id }));

	// Phones: the group's header and section strip pinned at the top of its four sections.
	const showNav = $derived(
		['/app/groups/[id]', '/app/groups/[id]/chat', '/app/groups/[id]/proposals', '/app/groups/[id]/timing'].includes(route)
	);
	const isAdmin = $derived(isAdminRole(data.group.role));
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

<div class="group-layout">
	<aside class="side">
		<GroupSidebar groups={data.groups} currentId={data.group.id} />
	</aside>
	<div class="content">
		{#if showNav}
			<div class="phone-nav">
				<header class="head">
					<IconButton href="/app" icon="chevron-left" label="Back to groups" />
					<div class="titles">
						<h1>{data.group.name}</h1>
						<span class="sub">{groupSubtitle(data.group)}</span>
					</div>
					<IconButton href="{base}/members" icon={isAdmin ? 'user-plus' : 'users'} label={isAdmin ? 'Members and invites' : 'Members'} />
				</header>
				<nav class="strip glass" aria-label="Group sections">
					{#each tabs as t (t.href)}
						<a href={t.href} class:on={t.current} aria-current={t.current ? 'page' : undefined}>
							<Icon name={t.icon} size={14} />
							<span>{t.label}</span>
							{#if t.badge}<span class="badge" aria-label="{t.badge} unread">{t.badge > 99 ? '99+' : t.badge}</span>{/if}
						</a>
					{/each}
				</nav>
			</div>
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
		flex-direction: column;
		gap: 10px;
		margin: calc(-1 * max(20px, env(safe-area-inset-top))) calc(-1 * var(--gutter)) 12px;
		padding: max(12px, env(safe-area-inset-top)) var(--gutter) 10px;
		background: linear-gradient(var(--bg) 75%, transparent);
	}
	.head {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.titles {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.head h1 {
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
	.strip {
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: 4px;
		padding: 4px;
		border-radius: 16px;
	}
	.strip a {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 5px;
		min-height: 36px;
		border-radius: 12px;
		font-size: 13px;
		font-weight: 500;
		color: var(--ink);
		text-decoration: none;
		white-space: nowrap;
	}
	.strip a.on {
		background: var(--solid);
		color: var(--on-solid);
		font-weight: 600;
	}
	.badge {
		min-width: 16px;
		height: 16px;
		padding: 0 5px;
		border-radius: 8px;
		background: var(--warn);
		color: #1a1b3a;
		font-size: 10px;
		font-weight: 700;
		line-height: 16px;
		text-align: center;
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
