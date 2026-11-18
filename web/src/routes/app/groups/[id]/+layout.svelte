<script lang="ts">
	import { page } from '$app/state';
	import { goto, invalidate } from '$app/navigation';
	import { live } from '#lib/client/live.svelte';
	import { unread } from '#lib/client/unread.svelte';
	import GroupSidebar from '#lib/components/groups/GroupSidebar.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import Menu from '#lib/components/ui/Menu.svelte';
	import { groupSubtitle, isAdminRole } from '#lib/client/members';
	import { toasts } from '#lib/client/toast.svelte';
	import { leavingGroups } from '#lib/client/groups';
	import { newGroupSheet } from '#lib/client/ui-state.svelte';

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
		},
		// Another group with the same people (anyone in it can start one).
		...(data.group.members.length > 1
			? [{ label: 'New group with these people', icon: 'plus' as const, onselect: () => newGroupSheet.showFrom(data.group) }]
			: []),
		{ label: 'Group settings', icon: 'settings' as const, onselect: () => goto(`${base}/settings`) }
	]);

	// Group details (name, members, roles, timing) change under us; refetch when they do.
	$effect(() => {
		const id = data.group.id;
		const off = live.on(['group.updated', 'member.changed'], (e) => {
			if (e.groupId !== id) return;
			// You were removed, or the group was deleted: it's gone, so back to your groups.
			const p = e.payload as { userId?: string; removed?: boolean } | null;
			if (e.type === 'member.changed' && p?.removed && p.userId === data.user.id) {
				// Left or deleted here: that page says so and goes back itself.
				if (leavingGroups.delete(id)) return;
				toasts.info(`You're no longer in ${data.group.name}`);
				goto('/app', { invalidateAll: true });
				return;
			}
			invalidate('app:group');
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
		margin: calc(-1 * max(20px, env(safe-area-inset-top))) calc(-1 * var(--gutter)) 4px;
		padding: max(12px, env(safe-area-inset-top)) var(--gutter) 18px;
	}
	/* A light wash of the page colour over a strong blur, fading out at the bottom so the
	   calendar scrolls away under the name without a hard edge. On a pseudo-element so the
	   mask doesn't clip the More menu's popover. */
	.phone-nav::before {
		content: '';
		position: absolute;
		inset: 0;
		z-index: -1;
		pointer-events: none;
		background: color-mix(in srgb, var(--bg) 45%, transparent);
		-webkit-backdrop-filter: blur(24px) saturate(160%);
		backdrop-filter: blur(24px) saturate(160%);
		-webkit-mask-image: linear-gradient(#000 calc(100% - 14px), transparent);
		mask-image: linear-gradient(#000 calc(100% - 14px), transparent);
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
