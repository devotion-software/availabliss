<script lang="ts">
	import type { Attachment } from '#lib/types';
	import { attachmentLabel, bubbleSrc, fitImage, formatBytes, gridTiles, isVisual, middleTruncate } from '#lib/client/attachments';
	import FileIcon from './FileIcon.svelte';

	/**
	 * A message's photos, GIFs and files inside its bubble: one picture sized by its shape, several as a
	 * 2-column grid of square tiles ("+N" past four); files as cards that download. Tapping a picture opens it.
	 */
	interface Props {
		attachments: Attachment[];
		mine: boolean;
		compact?: boolean;
		/** Opens the viewer at this picture (an index into the pictures only). */
		onopen?: (index: number) => void;
	}
	let { attachments, mine, compact = false, onopen }: Props = $props();

	const pictures = $derived(attachments.filter(isVisual));
	const files = $derived(attachments.filter((a) => !isVisual(a)));
	const maxW = $derived(compact ? 200 : 240);
	const single = $derived(pictures.length === 1 ? fitImage(pictures[0].width, pictures[0].height, maxW, compact ? 260 : 320) : null);
	const GAP = 3;
	const tile = $derived(Math.floor((maxW - GAP) / 2));
	const tiles = $derived(pictures.length > 1 ? gridTiles(pictures.length) : []);
</script>

{#if single}
	{@const a = pictures[0]}
	<button
		type="button"
		class="pic single"
		style:width="{single.width}px"
		style:aspect-ratio="{single.width} / {single.height}"
		aria-label="{attachmentLabel(a)}. Open" onclick={() => onopen?.(0)}>
		<img src={bubbleSrc(a)} alt="" width={single.width} height={single.height} loading="lazy" decoding="async" draggable="false" />
		{#if a.kind === 'gif'}<span class="badge" aria-hidden="true">GIF</span>{/if}
	</button>
{:else if tiles.length}
	<div class="grid" style:width="{maxW}px" style:--gap="{GAP}px">
		{#each tiles as t (t.index)}
			{@const a = pictures[t.index]}
			<button
				type="button"
				class="pic"
				class:wide={t.wide}
				aria-label="{attachmentLabel(a)} {t.index + 1} of {pictures.length}{t.more ? `, and ${t.more} more` : ''}. Open"
				onclick={() => onopen?.(t.index)}
			>
				<img src={bubbleSrc(a)} alt="" width={t.wide ? maxW : tile} height={tile} loading="lazy" decoding="async" draggable="false" />
				{#if a.kind === 'gif' && !t.more}<span class="badge" aria-hidden="true">GIF</span>{/if}
				{#if t.more}<span class="more" aria-hidden="true">+{t.more}</span>{/if}
			</button>
		{/each}
	</div>
{/if}

{#each files as f (f.id)}
	<a class="file" class:mine href={f.url} download={f.name} aria-label="{attachmentLabel(f)}. Download" title={f.name}>
		<span class="file-icon"><FileIcon name={f.name} mime={f.mime} /></span>
		<span class="file-text">
			<span class="file-name">{middleTruncate(f.name, compact ? 40 : 48)}</span>
			<span class="file-size">{formatBytes(f.size)}</span>
		</span>
	</a>
{/each}

<style>
	.pic {
		position: relative;
		display: block;
		overflow: hidden;
		border-radius: 14px;
		background: color-mix(in srgb, var(--ink) 8%, transparent);
		cursor: zoom-in;
		padding: 0;
		-webkit-touch-callout: none;
		user-select: none;
	}
	.pic img {
		display: block;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.pic:hover img {
		filter: brightness(0.95);
	}
	/* Shrinks with the bubble on narrow screens, keeping the tiles square. */
	.pic.single,
	.grid {
		max-width: 100%;
	}
	.grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: var(--gap);
		border-radius: 14px;
		overflow: hidden;
	}
	.grid .pic {
		border-radius: 4px;
		width: 100%;
		aspect-ratio: 1;
	}
	.grid .pic.wide {
		grid-column: span 2;
		aspect-ratio: 2;
	}
	.badge {
		position: absolute;
		left: 6px;
		bottom: 6px;
		padding: 1px 6px;
		border-radius: 6px;
		font-size: 10px;
		font-weight: 700;
		letter-spacing: 0.04em;
		background: rgb(0 0 0 / 0.55);
		color: #fff;
	}
	.more {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		background: rgb(0 0 0 / 0.45);
		color: #fff;
		font-size: 24px;
		font-weight: 650;
	}
	.file {
		display: flex;
		align-items: center;
		gap: 10px;
		min-width: min(200px, 100%);
		max-width: 280px;
		padding: 8px 12px 8px 8px;
		border-radius: 14px;
		background: var(--surface);
		color: var(--ink);
		text-decoration: none;
		-webkit-touch-callout: none;
	}
	.file.mine {
		background: color-mix(in srgb, var(--on-accent) 16%, transparent);
		color: var(--on-accent);
	}
	.file:hover {
		filter: brightness(0.97);
	}
	.file-icon {
		flex: none;
		width: 38px;
		height: 38px;
		border-radius: 11px;
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	.mine .file-icon {
		background: color-mix(in srgb, var(--on-accent) 22%, transparent);
		color: var(--on-accent);
	}
	.file-text {
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.file-name {
		font-size: 13px;
		font-weight: 600;
		line-height: 1.3;
		overflow-wrap: anywhere;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}
	.file-size {
		font-size: 11.5px;
		opacity: 0.75;
	}
</style>
