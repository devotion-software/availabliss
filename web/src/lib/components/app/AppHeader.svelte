<script lang="ts">
	import type { User } from '#lib/types';
	import Logo from '#lib/components/ui/Logo.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import ProfileMenu from './ProfileMenu.svelte';
	import { activity } from '#lib/client/activity.svelte';
	import { newGroupSheet } from '#lib/client/ui-state.svelte';
	import { page } from '$app/state';
	import Icon, { type IconName } from '#lib/components/ui/Icon.svelte';

	/** Desktop app bar (Web.dc.html): logo, main sections, New group, Activity bell, profile. Hidden on phones (they use the tab bar). */
	interface Props {
		user: User;
	}
	let { user }: Props = $props();

	const path = $derived(page.url.pathname);
	const sections: { href: string; label: string; icon: IconName; match: (p: string) => boolean }[] = [
		{ href: '/app', label: 'Groups', icon: 'groups', match: (p) => p === '/app' || p.startsWith('/app/groups') || p.startsWith('/app/proposals') },
		{ href: '/app/my-time', label: 'My time', icon: 'calendar', match: (p) => p.startsWith('/app/my-time') },
		{ href: '/app/calendars', label: 'Calendars', icon: 'calendar-sync', match: (p) => p.startsWith('/app/calendars') }
	];
</script>

<header class="app-header">
	<a class="home" href="/app" aria-label="Availabliss, your groups"><Logo size={34} wordmark /></a>
	<nav class="sections glass" aria-label="Main">
		{#each sections as s (s.href)}
			<a href={s.href} aria-current={s.match(path) ? 'page' : undefined}><Icon name={s.icon} size={16} />{s.label}</a>
		{/each}
	</nav>
	<span class="spacer"></span>
	<Button variant="glass" size="sm" icon="plus" class="new" onclick={newGroupSheet.show}>New group</Button>
	<IconButton
		href="/app/activity"
		icon="bell"
		label={activity.unread ? `Activity, ${activity.unread} unread` : 'Activity'}
		badge={activity.unread}
	/>
	<ProfileMenu {user} />
</header>

<style>
	.app-header {
		display: none;
		align-items: center;
		gap: 12px;
	}
	.home {
		text-decoration: none;
		border-radius: 12px;
	}
	.spacer {
		flex: 1;
	}
	.sections {
		margin-left: 20px;
		display: flex;
		gap: 2px;
		padding: 4px;
		border-radius: 16px;
	}
	.sections a {
		display: flex;
		align-items: center;
		gap: 7px;
		height: 36px;
		padding: 0 14px;
		border-radius: 12px;
		font-size: 14px;
		font-weight: 600;
		color: var(--muted);
		text-decoration: none;
		transition:
			background-color 0.2s ease,
			color 0.2s ease;
	}
	.sections a:hover {
		color: var(--ink);
	}
	.sections a[aria-current='page'] {
		background: var(--solid);
		color: var(--on-solid);
	}
	.app-header :global(.new) {
		height: 44px;
		border-radius: 14px;
		padding: 0 16px;
		font-size: 14px;
	}
	@media (min-width: 900px) {
		.app-header {
			display: flex;
		}
	}
</style>
