<script lang="ts">
	import type { Attachment } from '#lib/types';
	import {
		attachmentLabel,
		bubbleSrc,
		formatDuration,
		fitImage,
		formatBytes,
		gridTiles,
		isVisual,
		middleTruncate,
		opensWallet,
		passCard
	} from '#lib/client/attachments';
	import Icon from '#lib/components/ui/Icon.svelte';
	import FileIcon from './FileIcon.svelte';

	/**
	 * A message's photos, GIFs and files inside its bubble: one picture sized by its shape, several as a
	 * 2-column grid of square tiles ("+N" past four); files as cards that download; Wallet passes as a card
	 * drawn like the pass. Tapping a picture opens it.
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
	/** Safari (and any browser on iPhone / iPad) shows a linked .pkpass with Add to Wallet; others download it. */
	const wallet = typeof navigator !== 'undefined' && opensWallet(navigator.userAgent);
	const maxW = $derived(compact ? 200 : 240);
	const single = $derived(pictures.length === 1 ? fitImage(pictures[0].width, pictures[0].height, maxW, compact ? 260 : 320) : null);
	const GAP = 3;
	const tile = $derived(Math.floor((maxW - GAP) / 2));
	const tiles = $derived(pictures.length > 1 ? gridTiles(pictures.length) : []);
</script>

{#snippet play(a: Attachment)}
	<span class="play" aria-hidden="true">
		<svg width="20" height="20" viewBox="0 0 24 24"><path d="M8 5.5v13l11-6.5z" fill="currentColor" /></svg>
	</span>
	<span class="badge" aria-hidden="true">{formatDuration(a.duration)}</span>
{/snippet}

{#if single}
	{@const a = pictures[0]}
	<button
		type="button"
		class="pic single"
		style:width="{single.width}px"
		style:aspect-ratio="{single.width} / {single.height}"
		aria-label="{attachmentLabel(a)}. Open"
		onclick={() => onopen?.(0)}
	>
		{#if bubbleSrc(a)}<img
				src={bubbleSrc(a)}
				alt=""
				width={single.width}
				height={single.height}
				loading="lazy"
				decoding="async"
				draggable="false"
			/>{/if}
		{#if a.kind === 'gif'}<span class="badge" aria-hidden="true">GIF</span>{/if}
		{#if a.kind === 'video'}{@render play(a)}{/if}
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
				{#if bubbleSrc(a)}<img
						src={bubbleSrc(a)}
						alt=""
						width={t.wide ? maxW : tile}
						height={tile}
						loading="lazy"
						decoding="async"
						draggable="false"
					/>{/if}
				{#if a.kind === 'gif' && !t.more}<span class="badge" aria-hidden="true">GIF</span>{/if}
				{#if a.kind === 'video' && !t.more}{@render play(a)}{/if}
				{#if t.more}<span class="more" aria-hidden="true">+{t.more}</span>{/if}
			</button>
		{/each}
	</div>
{/if}

{#each files as f (f.id)}
	{#if f.kind === 'pass' && f.pass}
		{@const p = f.pass}
		{@const card = passCard(p)}
		<div
			class="pass"
			style:--pass-bg={p.backgroundColor}
			style:--pass-fg={p.foregroundColor}
			style:--pass-label={p.labelColor}
			role="group"
			aria-label={attachmentLabel(f)}
		>
			<div class="pass-head">
				{#if f.thumbnailUrl}<img class="pass-logo" src={f.thumbnailUrl} alt="" loading="lazy" decoding="async" draggable="false" />{/if}
				<span class="pass-org">{card.organization}</span>
				{#if p.voided}
					<span class="pass-voided">Voided</span>
				{:else}
					<span class="pass-kind" aria-hidden="true"><Icon name={p.style === 'boardingPass' ? 'plane' : 'ticket'} size={15} /></span>
				{/if}
			</div>
			<div class="pass-main">
				<span class="pass-title">{card.headline}</span>
				{#if card.date}<span class="pass-date">{card.date}</span>{/if}
				{#if card.venue}<span class="pass-venue">{card.venue}</span>{/if}
			</div>
			{#if card.fields.length}
				<dl class="pass-fields">
					{#each card.fields as field, i (i)}
						<div>
							{#if field.label}<dt>{field.label}</dt>{/if}
							<dd>{field.value}</dd>
						</div>
					{/each}
				</dl>
			{/if}
			{#if wallet && p.count === 1}
				<a class="pass-action wallet" href={f.url}>
					<Icon name="ticket" size={16} />Add to Apple Wallet
				</a>
			{:else}
				<a class="pass-action" href={f.url} download={f.name}>
					<Icon name="download" size={16} />{p.count > 1 ? `Download ${p.count} passes` : 'Download pass'}
				</a>
			{/if}
		</div>
	{:else}
		<a class="file" class:mine href={f.url} download={f.name} aria-label="{attachmentLabel(f)}. Download" title={f.name}>
			<span class="file-icon"><FileIcon name={f.name} mime={f.mime} /></span>
			<span class="file-text">
				<span class="file-name">{middleTruncate(f.name, compact ? 40 : 48)}</span>
				<span class="file-size">{formatBytes(f.size)}</span>
			</span>
		</a>
	{/if}
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
	.play {
		position: absolute;
		inset: 0;
		margin: auto;
		width: 48px;
		height: 48px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: #fff;
		background: rgba(0, 0, 0, 0.5);
		backdrop-filter: blur(6px);
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
	/* A Wallet pass, in its own colours (or the surface and ink when it has none). */
	.pass {
		--bg: var(--pass-bg, var(--surface));
		--fg: var(--pass-fg, var(--ink));
		--label: var(--pass-label, color-mix(in srgb, var(--fg) 72%, transparent));
		display: flex;
		flex-direction: column;
		gap: 10px;
		width: 240px;
		max-width: 100%;
		padding: 12px;
		border-radius: 14px;
		background: var(--bg);
		color: var(--fg);
		box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ink) 8%, transparent);
	}
	.pass-head {
		display: flex;
		align-items: center;
		gap: 8px;
		min-width: 0;
	}
	.pass-logo {
		max-height: 24px;
		max-width: 100px;
		object-fit: contain;
		object-position: left center;
	}
	.pass-org {
		flex: 1;
		min-width: 0;
		font-size: 13px;
		font-weight: 600;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.pass-kind {
		display: flex;
		color: var(--label);
	}
	.pass-voided {
		font-size: 10px;
		font-weight: 700;
		padding: 1px 6px;
		border-radius: 999px;
		box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--fg) 60%, transparent);
	}
	.pass-main {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.pass-title {
		font-size: 16px;
		font-weight: 650;
		line-height: 1.25;
		overflow-wrap: anywhere;
	}
	.pass-date {
		font-size: 13px;
		font-weight: 600;
		color: var(--label);
	}
	.pass-venue {
		font-size: 12px;
		opacity: 0.85;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.pass-fields {
		display: flex;
		gap: 14px;
		margin: 0;
		min-width: 0;
	}
	.pass-fields div {
		min-width: 0;
	}
	.pass-fields dt {
		font-size: 9px;
		font-weight: 600;
		letter-spacing: 0.04em;
		text-transform: uppercase;
		color: var(--label);
	}
	.pass-fields dd {
		margin: 0;
		font-size: 13px;
		font-weight: 600;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.pass-action {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		min-height: 40px;
		border-radius: 10px;
		font-size: 14px;
		font-weight: 600;
		text-decoration: none;
		color: var(--fg);
		background: color-mix(in srgb, var(--fg) 14%, transparent);
		-webkit-touch-callout: none;
	}
	.pass-action:hover {
		filter: brightness(0.96);
	}
	.pass-action.wallet {
		background: #000;
		color: #fff;
	}
</style>
