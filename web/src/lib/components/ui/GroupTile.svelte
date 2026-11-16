<script lang="ts">
	import type { TileColor } from '#lib/types';

	interface Props {
		monogram: string;
		color: TileColor | string;
		/** The group's photo; the coloured monogram shows when there's none (or it won't load). */
		image?: string | null;
		size?: number;
	}
	let { monogram, color, image = null, size = 48 }: Props = $props();
	const safeColor = $derived(/^t[1-5]$/.test(color) ? color : 't1');
	// Remembers which URL failed, so a new photo gets a fresh try.
	let failed = $state<string | null>(null);
	const src = $derived(image && image !== failed ? image : null);
</script>

<span class="tile" style:--size="{size}px" style:background="var(--{safeColor})" aria-hidden="true">
	{#if src}
		<img {src} alt="" onerror={() => (failed = src)} />
	{:else}
		{monogram}
	{/if}
</span>

<style>
	.tile {
		flex: none;
		width: var(--size);
		height: var(--size);
		border-radius: calc(var(--size) * 0.33);
		display: inline-flex;
		align-items: center;
		justify-content: center;
		overflow: hidden;
		font-family: var(--font-display);
		font-weight: 700;
		letter-spacing: -0.025em;
		font-size: calc(var(--size) * 0.36);
		color: var(--ink);
	}
	img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
</style>
