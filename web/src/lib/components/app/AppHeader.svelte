<script lang="ts">
	import type { User } from '#lib/types';
	import Logo from '#lib/components/ui/Logo.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import ProfileMenu from './ProfileMenu.svelte';
	import { activity } from '#lib/client/activity.svelte';
	import { newGroupSheet } from '#lib/client/ui-state.svelte';

	/** Desktop app bar (Web.dc.html): logo, New group, Activity bell, profile. Hidden on phones. */
	interface Props {
		user: User;
	}
	let { user }: Props = $props();
</script>

<header class="app-header">
	<a class="home" href="/app" aria-label="Availabliss, your groups"><Logo size={34} wordmark /></a>
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
