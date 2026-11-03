<script lang="ts">
	import type { GroupSummary } from '#lib/types';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import SyncRing from '#lib/components/ui/SyncRing.svelte';
	import AvatarStack from '#lib/components/ui/AvatarStack.svelte';
	import { groupBadge, syncText } from './status';
	import { unread } from '#lib/client/unread.svelte';

	/** A group on the home list (Main.dc): tile, name, status badge, timing, sync progress. */
	interface Props {
		group: GroupSummary;
		timeZone: string;
	}
	let { group, timeZone }: Props = $props();

	const badge = $derived(groupBadge(group, timeZone));
</script>

<a class="card glass" href="/app/groups/{group.id}">
	<GroupTile monogram={group.monogram} color={group.color} size={48} />
	<span class="body">
		<span class="row">
			<span class="name">{group.name}</span>
			<StatusPill tone={badge.tone}>{badge.text}</StatusPill>
		</span>
		<span class="timing">{group.requirementSummary}</span>
		<span class="row foot">
			<span class="members">
				{#if group.memberPreview?.length}
					<AvatarStack people={group.memberPreview.map((m) => ({ id: m.userId, name: m.name, image: m.image }))} max={5} size={22} />
				{:else}
					{group.memberCount} {group.memberCount === 1 ? 'member' : 'members'}
				{/if}
			</span>
			<span class="sync">
				<SyncRing value={group.syncedCount} total={group.memberCount} />
				{syncText(group)}
			</span>
			{#if unread.count(group)}
				<span class="unread" aria-label="{unread.count(group)} unread messages">{unread.count(group)}</span>
			{/if}
		</span>
	</span>
</a>

<style>
	.card {
		border-radius: 24px;
		padding: 14px;
		display: flex;
		gap: 12px;
		align-items: center;
		text-decoration: none;
		color: var(--ink);
		transition: transform 0.2s ease;
	}
	.card:hover {
		color: var(--ink);
		transform: translateY(-1px);
	}
	.body {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 5px;
	}
	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
	}
	.name {
		font-weight: 600;
		font-size: 16px;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.timing {
		font-size: 13px;
		color: var(--muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.foot {
		font-size: 12px;
		font-weight: 500;
		color: var(--muted);
	}
	.members {
		flex: 1;
		display: flex;
		align-items: center;
	}
	.sync {
		display: flex;
		align-items: center;
		gap: 6px;
	}
	.unread {
		min-width: 20px;
		height: 20px;
		padding: 0 6px;
		border-radius: 10px;
		background: var(--warn);
		color: #1a1b3a;
		font-size: 11px;
		font-weight: 700;
		line-height: 20px;
		text-align: center;
	}
</style>
