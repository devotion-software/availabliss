<script lang="ts">
	import { page } from '$app/state';
	import { invalidate } from '$app/navigation';
	import { live } from '#lib/client/live.svelte';
	import { activity } from '#lib/client/activity.svelte';
	import { newGroupSheet, upgradeSheet } from '#lib/client/ui-state.svelte';
	import Orbs from '#lib/components/ui/Orbs.svelte';
	import TabBar from '#lib/components/ui/TabBar.svelte';
	import AppHeader from '#lib/components/app/AppHeader.svelte';
	import NewGroupSheet from '#lib/components/groups/NewGroupSheet.svelte';
	import UpgradeSheet from '#lib/components/plan/UpgradeSheet.svelte';

	let { data, children } = $props();

	$effect.pre(() => {
		activity.seed(data.notifications);
	});
	$effect(() => activity.listen());

	// Keep group badges (matches, replies needed, unread) fresh. Bursts of events
	// collapse into one refetch.
	$effect(() => {
		let timer: ReturnType<typeof setTimeout> | undefined;
		const refresh = () => {
			clearTimeout(timer);
			timer = setTimeout(() => invalidate('app:groups'), 600);
		};
		const off = live.on(
			['group.updated', 'member.changed', 'proposal.created', 'proposal.updated', 'proposal.confirmed', 'availability.changed', 'message.created'],
			refresh
		);
		return () => {
			off();
			clearTimeout(timer);
		};
	});

	const path = $derived(page.url.pathname);
	// Group screens and proposals bring their own phone navigation.
	const mainTabs = $derived(!/^\/app\/(groups|proposals)\//.test(path));
	const tabs = $derived([
		{ href: '/app', label: 'Groups', icon: 'groups' as const, current: path === '/app' },
		{ href: '/app/my-time', label: 'My time', icon: 'calendar' as const, current: path.startsWith('/app/my-time') },
		{ href: '/app/activity', label: 'Activity', icon: 'bell' as const, current: path.startsWith('/app/activity'), badge: activity.unread > 0 },
		{
			href: '/app/settings',
			label: 'You',
			icon: 'user' as const,
			current: path.startsWith('/app/settings') || path.startsWith('/app/calendars')
		}
	]);
</script>

<Orbs />
<div class="shell" class:with-tabs={mainTabs}>
	<AppHeader user={data.user} />
	<main id="main">
		{@render children()}
	</main>
</div>
{#if mainTabs}
	<TabBar label="Main" {tabs} />
{/if}
<NewGroupSheet bind:open={newGroupSheet.open} user={data.user} />
<UpgradeSheet bind:open={upgradeSheet.open} user={data.user} reason={upgradeSheet.reason} />

<style>
	.shell {
		position: relative;
		z-index: 1;
		max-width: 1440px;
		margin: 0 auto;
		padding: max(20px, env(safe-area-inset-top)) var(--gutter) 32px;
		display: flex;
		flex-direction: column;
		gap: 20px;
		min-height: 100dvh;
	}
	.shell.with-tabs {
		padding-bottom: calc(var(--tabbar-space) + env(safe-area-inset-bottom));
	}
	main {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	@media (min-width: 900px) {
		.shell {
			--gutter: 24px;
		}
		.shell.with-tabs {
			padding-bottom: 32px;
		}
	}
</style>
