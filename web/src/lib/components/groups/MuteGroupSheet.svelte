<script lang="ts">
	import type { NotificationSettings } from '#lib/types';
	import { muteGroup, unmuteGroup } from '#lib/client/notification-settings';
	import { toasts } from '#lib/client/toast.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';

	/**
	 * Mute a group's pushes for a while or until you turn them back on (or unmute it), from the
	 * group's More menu. Activity still lists everything.
	 */
	interface Props {
		open: boolean;
		groupId: string;
		groupName: string;
		/** Whether the group is muted right now. */
		muted: boolean;
		/** When the mute ends, if it's for a while. */
		until?: string | null;
		onsaved: (settings: NotificationSettings) => void;
	}
	let { open = $bindable(), groupId, groupName, muted, until = null, onsaved }: Props = $props();

	const HOUR = 60 * 60 * 1000;
	const CHOICES = [
		{ key: '8h', label: 'For 8 hours', ms: 8 * HOUR },
		{ key: 'week', label: 'For a week', ms: 7 * 24 * HOUR },
		{ key: 'forever', label: 'Until I turn it back on', ms: null }
	] as const;

	let busy = $state<string | null>(null);

	const untilLabel = $derived(
		until ? new Date(until).toLocaleString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : null
	);

	async function run(key: string, action: () => Promise<NotificationSettings>, done: string) {
		busy = key;
		try {
			onsaved(await action());
			open = false;
			toasts.success(done);
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = null;
		}
	}

	function mute(choice: (typeof CHOICES)[number]) {
		const end = choice.ms === null ? null : new Date(Date.now() + choice.ms).toISOString();
		const done = choice.ms === null ? `Muted ${groupName}` : `Muted ${groupName} ${choice.label.toLowerCase()}`;
		run(choice.key, () => muteGroup(groupId, end), done);
	}
</script>

<Sheet
	bind:open
	title={muted ? 'Notifications muted' : 'Mute notifications'}
	description={muted
		? untilLabel
			? `No pushes from ${groupName} until ${untilLabel}. Activity still shows everything.`
			: `No pushes from ${groupName} until you turn them back on. Activity still shows everything.`
		: `No pushes from ${groupName} for a while. Activity still shows everything.`}
	width={420}
>
	<div class="choices">
		{#if muted}
			<Button variant="solid" icon="bell" block loading={busy === 'unmute'} disabled={busy !== null} onclick={() => run('unmute', () => unmuteGroup(groupId), `Unmuted ${groupName}`)}>
				Unmute
			</Button>
		{:else}
			{#each CHOICES as choice (choice.key)}
				<Button variant="secondary" block loading={busy === choice.key} disabled={busy !== null} onclick={() => mute(choice)}>{choice.label}</Button>
			{/each}
		{/if}
	</div>
</Sheet>

<style>
	.choices {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
</style>
