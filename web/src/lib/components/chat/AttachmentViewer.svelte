<script lang="ts">
	import { fade } from 'svelte/transition';
	import type { Attachment } from '#lib/types';
	import { downloadUrl } from '#lib/client/attachments';
	import { toasts } from '#lib/client/toast.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { focusTrap } from '#lib/components/ui/focus-trap';
	import { portal } from '#lib/components/ui/portal';

	/**
	 * Full-screen viewer for a message's photos and GIFs: ← / → (buttons, arrow keys or a swipe) between them,
	 * click or tap to zoom in 2.5× at that point (drag to pan, pinch on touch screens), Esc to close,
	 * Download, and Share where the browser can share files.
	 */
	interface Props {
		pictures: Attachment[];
		/** The picture showing; null when closed. */
		index: number | null;
		/** Who sent them, for the title. */
		title?: string;
		onclose: () => void;
		/** Adds Show in chat (from the media page): goes to the message the showing picture was sent in. */
		onshowinchat?: (index: number) => void;
	}
	let { pictures, index = $bindable(), title = 'Photo', onclose, onshowinchat }: Props = $props();

	const ZOOM = 2.5;
	const MAX_ZOOM = 4;

	const current = $derived(index !== null ? pictures[index] : undefined);
	const many = $derived(pictures.length > 1);

	let stage: HTMLElement | undefined = $state();
	let picture: HTMLImageElement | undefined = $state();
	let zoom = $state(1);
	let tx = $state(0);
	let ty = $state(0);
	let loaded = $state(false);
	let canShare = $state(false);
	let sharing = $state(false);

	$effect(() => {
		try {
			canShare = !!navigator.canShare?.({ files: [new File([''], 'photo.jpg', { type: 'image/jpeg' })] });
		} catch {
			canShare = false;
		}
	});

	// A different picture starts unzoomed.
	$effect(() => {
		void current?.id;
		zoom = 1;
		tx = 0;
		ty = 0;
		loaded = false;
	});

	function go(step: number) {
		if (index === null || !many) return;
		index = (index + step + pictures.length) % pictures.length;
	}

	function close() {
		index = null;
		onclose();
	}

	function onkeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') {
			e.preventDefault();
			e.stopPropagation();
			if (zoom > 1) resetZoom();
			else close();
		} else if (e.key === 'ArrowLeft') {
			e.preventDefault();
			go(-1);
		} else if (e.key === 'ArrowRight') {
			e.preventDefault();
			go(1);
		}
	}

	function resetZoom() {
		zoom = 1;
		tx = 0;
		ty = 0;
	}

	/** Keeps the zoomed picture over the stage: it pans only as far as its edges (and stays centred where it's smaller). */
	function clamp() {
		if (!stage) return;
		const { width, height } = stage.getBoundingClientRect();
		// The picture's laid-out (unzoomed) size.
		const w = picture?.offsetWidth || width;
		const h = picture?.offsetHeight || height;
		const mx = Math.max(0, (w * zoom - width) / 2);
		const my = Math.max(0, (h * zoom - height) / 2);
		tx = Math.max(-mx, Math.min(mx, tx));
		ty = Math.max(-my, Math.min(my, ty));
	}

	/** Zooms to `z` keeping the point under (clientX, clientY) where it is. */
	function zoomAt(z: number, clientX: number, clientY: number) {
		if (!stage) return;
		const r = stage.getBoundingClientRect();
		// The point relative to the stage centre, in unzoomed picture coordinates.
		const px = (clientX - r.left - r.width / 2 - tx) / zoom;
		const py = (clientY - r.top - r.height / 2 - ty) / zoom;
		zoom = z;
		tx = clientX - r.left - r.width / 2 - px * z;
		ty = clientY - r.top - r.height / 2 - py * z;
		if (z <= 1) resetZoom();
		else clamp();
	}

	// Pointers: a drag pans (zoomed) or swipes (not zoomed); two fingers pinch; a tap toggles zoom.
	const pointers = new Map<number, { x: number; y: number }>();
	let start: { x: number; y: number; tx: number; ty: number; onPicture: boolean } | null = null;
	let pinch: { dist: number; zoom: number } | null = null;
	let moved = false;
	let dragging = $state(false);

	const distance = () => {
		const [a, b] = [...pointers.values()];
		return Math.hypot(a.x - b.x, a.y - b.y);
	};

	function onpointerdown(e: PointerEvent) {
		if (e.button !== 0) return;
		(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
		pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
		dragging = true;
		if (pointers.size === 1) {
			// Pointer capture retargets the release to the stage, so note now whether it began on the picture.
			start = { x: e.clientX, y: e.clientY, tx, ty, onPicture: e.target instanceof HTMLImageElement };
			moved = false;
		} else if (pointers.size === 2) {
			pinch = { dist: distance(), zoom };
			moved = true;
		}
	}

	function onpointermove(e: PointerEvent) {
		if (!pointers.has(e.pointerId)) return;
		pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
		if (pinch && pointers.size === 2) {
			const [a, b] = [...pointers.values()];
			const z = Math.max(1, Math.min(MAX_ZOOM, (pinch.zoom * distance()) / pinch.dist));
			zoomAt(z, (a.x + b.x) / 2, (a.y + b.y) / 2);
			return;
		}
		if (!start) return;
		const dx = e.clientX - start.x;
		const dy = e.clientY - start.y;
		if (Math.hypot(dx, dy) > 6) moved = true;
		if (zoom > 1) {
			tx = start.tx + dx;
			ty = start.ty + dy;
			clamp();
		}
	}

	function onpointerup(e: PointerEvent) {
		if (!pointers.has(e.pointerId)) return;
		pointers.delete(e.pointerId);
		if (pointers.size > 0) return;
		dragging = false;
		const s = start;
		start = null;
		if (pinch) {
			pinch = null;
			if (zoom < 1.05) resetZoom();
			return;
		}
		if (!s) return;
		const dx = e.clientX - s.x;
		const dy = e.clientY - s.y;
		if (!moved) {
			// A tap on the picture toggles zoom; on the dark space around it, closes.
			if (s.onPicture) zoomAt(zoom > 1 ? 1 : ZOOM, e.clientX, e.clientY);
			else if (zoom > 1) resetZoom();
			else close();
		} else if (zoom === 1 && Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) {
			go(dx < 0 ? 1 : -1);
		} else if (zoom === 1 && dy > 100 && Math.abs(dy) > Math.abs(dx) * 1.5) {
			close();
		}
	}

	function onpointercancel(e: PointerEvent) {
		pointers.delete(e.pointerId);
		if (!pointers.size) {
			dragging = false;
			start = null;
			pinch = null;
		}
	}

	function onwheel(e: WheelEvent) {
		// Pinch on a trackpad arrives as ctrl+wheel.
		if (!e.ctrlKey && zoom === 1) return;
		e.preventDefault();
		if (e.ctrlKey) zoomAt(Math.max(1, Math.min(MAX_ZOOM, zoom * Math.exp(-e.deltaY / 100))), e.clientX, e.clientY);
		else {
			tx -= e.deltaX;
			ty -= e.deltaY;
			clamp();
		}
	}

	async function share() {
		if (!current || sharing) return;
		sharing = true;
		try {
			const blob = await fetch(current.url).then((r) => {
				if (!r.ok) throw new Error('Couldn’t get the photo to share');
				return r.blob();
			});
			const file = new File([blob], current.name, { type: blob.type || current.mime });
			await navigator.share({ files: [file] });
		} catch (err) {
			if (!(err instanceof DOMException && err.name === 'AbortError')) toasts.error(err);
		} finally {
			sharing = false;
		}
	}

	const nonPassive = (node: HTMLElement) => {
		node.addEventListener('wheel', onwheel, { passive: false });
		return { destroy: () => node.removeEventListener('wheel', onwheel) };
	};
</script>

{#if current && index !== null}
	<div
		class="viewer"
		role="dialog"
		aria-modal="true"
		aria-label="{title}{many ? `, ${index + 1} of ${pictures.length}` : ''}"
		tabindex="-1"
		use:portal
		use:focusTrap
		{onkeydown}
		transition:fade={{ duration: 160 }}
	>
		<div class="bar top">
			<span class="count" aria-live="polite">{many ? `${index + 1} of ${pictures.length}` : current.kind === 'gif' ? 'GIF' : 'Photo'}</span>
			{#if canShare}
				<button type="button" class="tool" aria-label="Share" title="Share" disabled={sharing} onclick={share}>
					<Icon name="share" size={20} />
				</button>
			{/if}
			{#if onshowinchat}
				<button type="button" class="tool" aria-label="Show in chat" title="Show in chat" onclick={() => index !== null && onshowinchat(index)}>
					<Icon name="chat" size={20} />
				</button>
			{/if}
			<a class="tool" href={downloadUrl(current.url)} download={current.name} aria-label="Download" title="Download">
				<Icon name="download" size={20} />
			</a>
			<button type="button" class="tool" aria-label="Close" title="Close" onclick={close}>
				<Icon name="x" size={22} strokeWidth={2.4} />
			</button>
		</div>

		{#if current.kind === 'video'}
			<!-- A video plays with the browser's own controls (no zoom or swipe on top of them). -->
			<div class="stage">
				{#key current.id}
					<!-- svelte-ignore a11y_media_has_caption -->
					<video class="pic video" src={current.url} poster={current.thumbnailUrl ?? undefined} controls autoplay playsinline preload="metadata"
					></video>
				{/key}
			</div>
		{:else}
			<div
				class="stage"
				class:zoomed={zoom > 1}
				bind:this={stage}
				role="presentation"
				use:nonPassive
				{onpointerdown}
				{onpointermove}
				{onpointerup}
				{onpointercancel}
			>
				{#key current.id}
					{#if current.thumbnailUrl && !loaded}
						<img
							class="pic preview"
							src={current.thumbnailUrl}
							alt=""
							width={current.width ?? undefined}
							height={current.height ?? undefined}
							draggable="false"
							style:transform="translate({tx}px, {ty}px) scale({zoom})"
						/>
					{/if}
					<img
						bind:this={picture}
						class="pic"
						src={current.url}
						alt={current.kind === 'gif' ? 'GIF' : 'Photo'}
						width={current.width ?? undefined}
						height={current.height ?? undefined}
						draggable="false"
						onload={() => (loaded = true)}
						style:transform="translate({tx}px, {ty}px) scale({zoom})"
						style:transition={dragging ? 'none' : undefined}
					/>
				{/key}
			</div>
		{/if}

		{#if many}
			<button type="button" class="nav prev" aria-label="Previous photo" onclick={() => go(-1)}>
				<Icon name="chevron-left" size={26} strokeWidth={2.4} />
			</button>
			<button type="button" class="nav next" aria-label="Next photo" onclick={() => go(1)}>
				<Icon name="chevron-right" size={26} strokeWidth={2.4} />
			</button>
		{/if}
	</div>
{/if}

<style>
	.viewer {
		position: fixed;
		inset: 0;
		/* Above sheets and the message menu. */
		z-index: 120;
		background: #0b0b0f;
		color: #fff;
		display: flex;
		flex-direction: column;
		outline: none;
	}
	.bar {
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		z-index: 2;
		display: flex;
		align-items: center;
		gap: 4px;
		padding: max(10px, env(safe-area-inset-top)) 10px 10px 18px;
		background: linear-gradient(to bottom, rgb(0 0 0 / 0.5), transparent);
	}
	.count {
		flex: 1;
		font-size: 14px;
		font-weight: 600;
	}
	.tool,
	.nav {
		width: 44px;
		height: 44px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: #fff;
		background: rgb(255 255 255 / 0.08);
		transition: background-color 0.15s ease;
	}
	.tool:hover,
	.nav:hover {
		background: rgb(255 255 255 / 0.2);
	}
	.tool:focus-visible,
	.nav:focus-visible {
		outline: 2px solid #fff;
		outline-offset: 2px;
	}
	.video {
		max-width: 100%;
		max-height: 100%;
		background: #000;
	}
	.stage {
		position: relative;
		flex: 1;
		min-height: 0;
		overflow: hidden;
		touch-action: none;
		display: flex;
		align-items: center;
		justify-content: center;
		cursor: zoom-out;
	}
	.pic {
		position: absolute;
		inset: 0;
		margin: auto;
		max-width: 100%;
		max-height: 100%;
		width: auto;
		height: auto;
		object-fit: contain;
		cursor: zoom-in;
		user-select: none;
		-webkit-user-select: none;
		-webkit-touch-callout: none;
		transition: transform 0.22s var(--ease-out, ease-out);
	}
	.zoomed .pic {
		cursor: grab;
	}
	.preview {
		filter: blur(2px);
	}
	.nav {
		position: absolute;
		top: 50%;
		translate: 0 -50%;
		z-index: 2;
	}
	.prev {
		left: 12px;
	}
	.next {
		right: 12px;
	}
	@media (hover: none) {
		/* Swipe on touch screens; the buttons stay for screen readers and keyboards. */
		.nav:not(:focus-visible) {
			opacity: 0;
			pointer-events: none;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.pic {
			transition: none;
		}
	}
</style>
