<script lang="ts">
	import type { TileColor } from '#lib/types';

	interface Props {
		monogram: string;
		color: TileColor | string;
		size?: number;
	}
	let { monogram, color, size = 48 }: Props = $props();
	const safeColor = $derived(/^t[1-5]$/.test(color) ? color : 't1');
</script>

<span class="tile" style:--size="{size}px" style:background="var(--{safeColor})" aria-hidden="true">{monogram}</span>

<style>
	.tile {
		flex: none;
		width: var(--size);
		height: var(--size);
		border-radius: calc(var(--size) * 0.33);
		display: inline-flex;
		align-items: center;
		justify-content: center;
		font-family: var(--font-display);
		font-weight: 700;
		letter-spacing: -0.025em;
		font-size: calc(var(--size) * 0.36);
		color: var(--ink);
	}
</style>
