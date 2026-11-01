<script lang="ts">
	import type { ManualBlock } from '#lib/types';
	import Icon from '#lib/components/ui/Icon.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import { blockSummary } from './parts';

	interface Props {
		block: ManualBlock;
		timeZone: string;
		onedit: () => void;
		ondelete: () => void;
	}
	let { block, timeZone, onedit, ondelete }: Props = $props();
	const title = $derived(block.note || 'Busy');
</script>

<div class="glass block">
	<span class="tile"><Icon name={block.repeat === 'none' ? 'calendar' : 'repeat'} size={17} strokeWidth={2.2} /></span>
	<button type="button" class="text" onclick={onedit} aria-label="Edit {title}, {blockSummary(block, timeZone)}">
		<span class="title">{title}</span>
		<span class="sub">{blockSummary(block, timeZone)}</span>
	</button>
	{#if block.note}<StatusPill>Private</StatusPill>{/if}
	<IconButton icon="trash" label="Delete {title}" variant="plain" size={40} onclick={ondelete} />
</div>

<style>
	.block {
		border-radius: 20px;
		padding: 8px 6px 8px 12px;
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.tile {
		width: 34px;
		height: 34px;
		flex: none;
		border-radius: 11px;
		background: var(--accent-soft);
		color: var(--accent-ink);
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.text {
		flex: 1;
		min-width: 0;
		min-height: 44px;
		display: flex;
		flex-direction: column;
		justify-content: center;
		text-align: left;
		border-radius: 10px;
	}
	.title {
		font-size: 14px;
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
	}
</style>
