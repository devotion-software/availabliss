<script lang="ts">
	import { untrack } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import type { AppNotification } from '#lib/types';
	import { activity } from '#lib/client/activity.svelte';
	import { notificationHref } from '#lib/client/notifications';
	import { useFormat } from '#lib/client/format';
	import Icon, { type IconName } from '#lib/components/ui/Icon.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { ChatsList, chatPreview } from '#lib/client/chats.svelte';

	let { data } = $props();
	const fmt = useFormat();
	// The layout's data until the shared store is seeded (SSR and first render).
	const all = $derived(activity.seeded ? activity.items : data.notifications.notifications);
	// Chats have their own tab, so Updates is everything else.
	const items = $derived(all.filter((n) => n.kind !== 'message'));

	// Chats (like a messaging app) or Updates; ?tab=updates keeps it across reloads and back.
	type Tab = 'chats' | 'updates';
	const tab = $derived<Tab>(page.url.searchParams.get('tab') === 'updates' ? 'updates' : 'chats');
	function setTab(next: Tab) {
		goto(next === 'chats' ? '/app/activity' : '/app/activity?tab=updates', { replaceState: true });
	}

	const chats = new ChatsList();
	$effect(() => {
		chats.load();
		return chats.listen();
	});
	const meId = $derived(data.user.id);

	const ICON: Record<AppNotification['kind'], IconName> = {
		'proposal.created': 'check-circle',
		'proposal.confirmed': 'sparkle',
		'proposal.changed': 'alert',
		nudge: 'megaphone',
		'invite.accepted': 'user-plus',
		'invite.received': 'user-plus',
		message: 'chat',
		'event.created': 'calendar-plus',
		'role.changed': 'shield',
		reminder: 'clock'
	};

	// Opening Activity marks everything read, and so does anything new arriving while
	// it's open; unread rows keep their highlight for this visit.
	const unreadIds = new SvelteSet(
		untrack(() => items)
			.filter((n) => !n.readAt)
			.map((n) => n.id)
	);
	$effect(() => {
		if (!activity.unread) return;
		untrack(() => {
			for (const n of activity.items) if (!n.readAt) unreadIds.add(n.id);
			activity.markAllRead();
		});
	});

	/** The group's tile colour behind the icon (`var(--t3)`), or nothing for items without a group. */
	const tileOf = (n: AppNotification) => (n.groupColor && /^t[1-5]$/.test(n.groupColor) ? `var(--${n.groupColor})` : undefined);
</script>

<svelte:head><title>Activity · Availabliss</title></svelte:head>

<div class="activity">
	<PageHeader
		title="Activity"
		subtitle="Your chats, and proposals, plans and nudges from your groups"
		backHref="/app"
		backLabel="Back to groups"
		backOnPhoneOnly
	>
		{#snippet actions()}
			<IconButton href="/app/plans" icon="calendar" label="My plans" />
		{/snippet}
	</PageHeader>

	<SegmentedControl
		options={[
			{ value: 'chats', label: 'Chats', icon: 'chat' },
			{ value: 'updates', label: 'Updates', icon: 'bell' }
		]}
		value={tab}
		onchange={setTab}
		label="Show"
		block
	/>

	{#if tab === 'chats'}
		{#if chats.items.length}
			<ul class="list glass" aria-live="polite">
				{#each chats.items as c (c.group.id)}
					<li>
						<a href="/app/groups/{c.group.id}/chat?from=activity" class:unread={c.unreadCount > 0}>
							<GroupTile monogram={c.group.monogram} color={c.group.color} image={c.group.image} size={44} />
							<span class="text">
								<span class="title">{c.group.name}</span>
								<span class="body">{c.lastMessage ? chatPreview(c.lastMessage, meId) : 'No messages yet. Say hello!'}</span>
							</span>
							<span class="side">
								{#if c.lastMessage}<time datetime={c.lastMessage.createdAt}>{fmt.current.relative(c.lastMessage.createdAt)}</time>{/if}
								{#if c.unreadCount}<span class="badge" aria-label="{c.unreadCount} unread">{c.unreadCount > 99 ? '99+' : c.unreadCount}</span>{/if}
							</span>
						</a>
					</li>
				{/each}
			</ul>
		{:else if chats.failed}
			<div class="glass empty"><EmptyState icon="chat" title="Couldn't load your chats" message="Check your connection and try again." /></div>
		{:else if chats.loaded}
			<div class="glass empty">
				<EmptyState icon="chat" title="No chats yet" message="Each group you're in has a chat. Make a group, or join one from an invite." />
			</div>
		{/if}
	{:else if items.length}
		<ul class="list glass" aria-live="polite">
			{#each items as n (n.id)}
				<li>
					<a href={notificationHref(n)} class:unread={unreadIds.has(n.id) || !n.readAt}>
						<span class="icon" class:grouped={!!tileOf(n)} style:--tile={tileOf(n)}><Icon name={ICON[n.kind] ?? 'bell'} size={18} /></span>
						<span class="text">
							<span class="title">
								{n.title}{#if n.kind === 'message' && !n.readAt && Number(n.data.count) > 1}<span class="count">{n.data.count} new</span>{/if}
							</span>
							<span class="body">{n.body}</span>
						</span>
						<time datetime={n.createdAt}>{fmt.current.relative(n.createdAt)}</time>
					</a>
				</li>
			{/each}
		</ul>
	{:else}
		<div class="glass empty">
			<EmptyState icon="bell" title="All quiet" message="When someone proposes a time, replies or nudges you, it shows up here." />
		</div>
	{/if}
</div>

<style>
	.activity {
		display: flex;
		flex-direction: column;
		gap: 16px;
		max-width: 720px;
		width: 100%;
		margin: 0 auto;
	}
	.list {
		list-style: none;
		margin: 0;
		padding: 4px 8px;
		border-radius: 24px;
	}
	li + li {
		border-top: 1px solid var(--line);
	}
	a {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 64px;
		padding: 10px 6px;
		border-radius: 16px;
		text-decoration: none;
		color: var(--ink);
	}
	a:hover {
		background: var(--surface);
		color: var(--ink);
	}
	.icon {
		width: 38px;
		height: 38px;
		flex: none;
		border-radius: 12px;
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--line);
		color: var(--neutral-ink);
	}
	.unread .icon {
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	/* On the group's own colour, so you can tell groups apart at a glance. */
	.icon.grouped {
		background: var(--tile);
		color: var(--ink);
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.title {
		font-size: 15px;
		font-weight: 600;
	}
	.body {
		font-size: 13px;
		color: var(--muted);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	time {
		flex: none;
		font-size: 12px;
		color: var(--muted);
	}
	.unread time {
		color: var(--accent-ink);
		font-weight: 600;
	}
	.empty {
		border-radius: 28px;
	}
	.side {
		flex: none;
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		gap: 4px;
	}
	.badge {
		min-width: 20px;
		height: 20px;
		padding: 0 6px;
		border-radius: 10px;
		background: var(--accent);
		color: var(--on-accent);
		font-size: 11px;
		font-weight: 700;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.count {
		margin-left: 8px;
		padding: 1px 7px;
		border-radius: 999px;
		background: var(--accent);
		color: var(--on-accent);
		font-size: 11px;
		font-weight: 700;
		vertical-align: 1px;
	}
</style>
