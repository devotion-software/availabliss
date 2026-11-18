<script lang="ts">
	import type { AttachmentDraft } from '#lib/client/attachment-draft.svelte';
	import { formatBytes, middleTruncate } from '#lib/client/attachments';
	import Icon from '#lib/components/ui/Icon.svelte';
	import FileIcon from './FileIcon.svelte';

	/**
	 * The attachments picked for the next message, above the composer: picture thumbnails and file chips,
	 * each with its upload progress (a ring), ✕ to remove, and on failure a retry.
	 */
	interface Props {
		draft: AttachmentDraft;
		compact?: boolean;
	}
	let { draft, compact = false }: Props = $props();

	const R = 13;
	const C = 2 * Math.PI * R;
</script>

<ul class="tray" class:compact aria-label="Attachments to send">
	{#each draft.items as item (item.key)}
		{@const label = item.kind === 'file' ? `${item.file.name}, ${formatBytes(item.file.size)}` : item.kind === 'gif' ? 'GIF' : 'Photo'}
		{@const state = item.status === 'uploading' ? `uploading, ${Math.round(item.progress * 100)}%` : item.status === 'error' ? `failed: ${item.error}` : 'ready'}
		<li class="item" class:file={item.kind === 'file'} class:failed={item.status === 'error'} aria-label="{label}, {state}">
			{#if item.kind !== 'file'}
				{#if item.preview}
					<img src={item.preview} alt="" />
				{:else}
					<span class="placeholder"><Icon name="image" size={22} /></span>
				{/if}
			{:else}
				<span class="file-icon"><FileIcon name={item.file.name} mime={item.file.type} size={20} /></span>
				<span class="file-text" aria-hidden="true">
					<span class="file-name">{middleTruncate(item.file.name, 16)}</span>
					<span class="file-size">{formatBytes(item.file.size)}</span>
				</span>
			{/if}

			{#if item.status === 'uploading'}
				<span class="overlay" aria-hidden="true">
					<svg width="32" height="32" viewBox="0 0 32 32" class:spin={item.progress === 0}>
						<circle cx="16" cy="16" r={R} class="track" />
						<circle
							cx="16"
							cy="16"
							r={R}
							class="ring"
							stroke-dasharray={C}
							stroke-dashoffset={C * (1 - Math.max(0.06, item.progress))}
						/>
					</svg>
				</span>
			{:else if item.status === 'error'}
				<button type="button" class="overlay retry" title={item.error ?? 'Upload failed'} aria-label="Retry {label}" onclick={() => draft.retry(item.key)}>
					<Icon name="refresh" size={20} strokeWidth={2.4} />
				</button>
			{/if}

			<button type="button" class="remove" aria-label="Remove {label}" title="Remove" onclick={() => draft.remove(item.key)}>
				<Icon name="x" size={12} strokeWidth={3} />
			</button>
		</li>
	{/each}
</ul>

<style>
	.tray {
		list-style: none;
		margin: 0;
		padding: 6px 4px 2px;
		display: flex;
		gap: 8px;
		overflow-x: auto;
		scrollbar-width: thin;
	}
	.item {
		position: relative;
		flex: none;
		width: 64px;
		height: 64px;
		border-radius: 14px;
		background: var(--surface);
		border: 1px solid var(--line-2, var(--glass-border));
		display: flex;
		align-items: center;
	}
	.compact .item {
		width: 56px;
		height: 56px;
	}
	.item.file {
		width: 168px;
		gap: 8px;
		padding: 0 10px;
	}
	img,
	.placeholder {
		width: 100%;
		height: 100%;
		border-radius: inherit;
		object-fit: cover;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--muted);
	}
	.file-icon {
		flex: none;
		width: 34px;
		height: 34px;
		border-radius: 10px;
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	.file-text {
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.file-name {
		font-size: 12px;
		font-weight: 600;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.file-size {
		font-size: 11px;
		color: var(--muted);
	}
	.overlay {
		position: absolute;
		inset: 0;
		border-radius: inherit;
		display: flex;
		align-items: center;
		justify-content: center;
		background: color-mix(in srgb, var(--bg) 55%, transparent);
		color: var(--ink);
	}
	.retry {
		background: color-mix(in srgb, var(--warn-bg, var(--bg)) 80%, transparent);
		color: var(--warn-ink, var(--ink));
		cursor: pointer;
	}
	.failed {
		border-color: var(--warn-ink, red);
	}
	svg {
		transform: rotate(-90deg);
	}
	svg.spin {
		animation: spin 1s linear infinite;
	}
	circle {
		fill: none;
		stroke-width: 3;
	}
	.track {
		stroke: color-mix(in srgb, var(--ink) 15%, transparent);
	}
	.ring {
		stroke: var(--accent);
		stroke-linecap: round;
		transition: stroke-dashoffset 0.2s ease;
	}
	.remove {
		position: absolute;
		top: -6px;
		right: -6px;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--solid);
		color: var(--on-solid);
		box-shadow: 0 0 0 2px var(--bg);
	}
	/* A bigger target than it looks. */
	.remove::after {
		content: '';
		position: absolute;
		inset: -8px;
	}
	@keyframes spin {
		from {
			transform: rotate(-90deg);
		}
		to {
			transform: rotate(270deg);
		}
	}
</style>
