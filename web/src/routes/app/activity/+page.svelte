<script lang="ts">
	import { untrack } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import type { AppNotification } from '#lib/types';
	import { activity } from '#lib/client/activity.svelte';
	import { notificationHref } from '#lib/client/notifications';
	import { useFormat } from '#lib/client/format';
	import Icon, { type IconName } from '#lib/components/ui/Icon.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';

	let { data } = $props();
	const fmt = useFormat();
	// The layout's data until the shared store is seeded (SSR and first render).
	const items = $derived(activity.seeded ? activity.items : data.notifications.notifications);

	const ICON: Record<AppNotification['kind'], IconName> = {
		'proposal.created': 'check-circle',
		'proposal.confirmed': 'sparkle',
		nudge: 'megaphone',
		'invite.accepted': 'user-plus',
		message: 'chat',
		'event.created': 'calendar-plus'
	};

	// Opening Activity marks everything read, and so does anything new arriving while
	// it's open; unread rows keep their highlight for this visit.
	const unreadIds = new SvelteSet(untrack(() => items).filter((n) => !n.readAt).map((n) => n.id));
	$effect(() => {
		if (!activity.unread) return;
		untrack(() => {
			for (const n of activity.items) if (!n.readAt) unreadIds.add(n.id);
			activity.markAllRead();
		});
	});
</script>

<svelte:head><title>Activity · Availabliss</title></svelte:head>

<div class="activity">
	<PageHeader title="Activity" subtitle="Proposals, replies and nudges from your groups" backHref="/app" backLabel="Back to groups" backOnPhoneOnly />

	{#if items.length}
		<ul class="list glass" aria-live="polite">
			{#each items as n (n.id)}
				<li>
					<a href={notificationHref(n)} class:unread={unreadIds.has(n.id) || !n.readAt}>
						<span class="icon"><Icon name={ICON[n.kind] ?? 'bell'} size={18} /></span>
						<span class="text">
							<span class="title">{n.title}</span>
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
</style>
