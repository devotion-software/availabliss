<script lang="ts">
	import type { Member } from '#lib/types';
	import { firstName, listNames } from '#lib/client/format';
	import { nudgeGroup } from '#lib/client/groups';
	import { toasts } from '#lib/client/toast.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import SyncRing from '#lib/components/ui/SyncRing.svelte';

	/** "2 people still to sync" with a Nudge for admins. Renders nothing once everyone's synced. */
	interface Props {
		groupId: string;
		members: readonly Member[];
		canNudge: boolean;
		/** Desktop: slim pill inside the calendar header. */
		variant?: 'banner' | 'pill';
	}
	let { groupId, members, canNudge, variant = 'banner' }: Props = $props();

	const pending = $derived(members.filter((m) => !m.synced));
	const synced = $derived(members.length - pending.length);
	const names = $derived(listNames(pending.map((m) => firstName(m.name)), 2));
	let busy = $state(false);

	async function nudge() {
		busy = true;
		try {
			const { nudged } = await nudgeGroup(groupId);
			toasts.success(nudged ? `Nudged ${nudged} ${nudged === 1 ? 'person' : 'people'}` : 'Everyone has already been nudged');
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

{#if pending.length}
	{#if variant === 'pill'}
		<span class="pill">
			<strong>{synced} of {members.length} synced</strong>
			<span>· {names} to go</span>
			{#if canNudge}
				<button type="button" onclick={nudge} disabled={busy}>Nudge</button>
			{/if}
		</span>
	{:else}
		<div class="banner glass">
			<SyncRing value={synced} total={members.length} size={38} stroke={4} tone="warn" label="{synced} of {members.length} synced">
				<span class="count">{synced}/{members.length}</span>
			</SyncRing>
			<div class="text">
				<span class="title">{pending.length} {pending.length === 1 ? 'person' : 'people'} still to sync</span>
				<span class="sub">{names} {pending.length === 1 ? "hasn't" : "haven't"} added busy times</span>
			</div>
			{#if canNudge}
				<Button variant="warn" size="sm" class="nudge" loading={busy} onclick={nudge}>Nudge</Button>
			{/if}
		</div>
	{/if}
{/if}

<style>
	.banner {
		border-radius: 20px;
		padding: 10px 10px 10px 12px;
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.count {
		font-size: 11px;
		font-weight: 700;
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.title {
		font-size: 14px;
		font-weight: 600;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
	}
	.banner :global(.nudge) {
		height: 36px;
		padding: 0 12px;
	}
	.pill {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		font-size: 13px;
		padding: 4px 4px 4px 12px;
		border-radius: 999px;
		background: var(--warn-bg);
		color: var(--warn-ink);
		min-height: 32px;
	}
	.pill strong {
		font-weight: 600;
	}
	.pill button {
		height: 28px;
		padding: 0 10px;
		border-radius: 999px;
		font-size: 12px;
		font-weight: 700;
		color: var(--warn-ink);
		background: color-mix(in srgb, var(--warn) 22%, transparent);
	}
</style>
