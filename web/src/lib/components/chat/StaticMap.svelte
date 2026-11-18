<script lang="ts">
	import { mapTiles } from '#lib/client/location';
	import Icon from '#lib/components/ui/Icon.svelte';

	/**
	 * A still map of `lat`, `lng` from OpenStreetMap's standard tiles (no API key), with a pin in the
	 * middle and the © OpenStreetMap credit the tiles need. Fills its box; `height` sets how tall.
	 */
	interface Props {
		lat: number;
		lng: number;
		height: number;
		zoom?: number;
	}
	let { lat, lng, height, zoom = 16 }: Props = $props();

	let width = $state(0);
	const tiles = $derived(width ? mapTiles(lat, lng, width, height, zoom) : []);
</script>

<div class="map" style:height="{height}px" bind:clientWidth={width} aria-hidden="true">
	{#each tiles as tile (tile.url + tile.left)}
		<img
			src={tile.url}
			alt=""
			loading="lazy"
			decoding="async"
			referrerpolicy="strict-origin-when-cross-origin"
			style:left="{tile.left}px"
			style:top="{tile.top}px"
		/>
	{/each}
	<span class="pin"><Icon name="map-pin" size={17} strokeWidth={2.4} /></span>
	<span class="credit">© OpenStreetMap</span>
</div>

<style>
	.map {
		position: relative;
		overflow: hidden;
		width: 100%;
		background: var(--accent-soft);
	}
	img {
		position: absolute;
		width: 128px;
		height: 128px;
		max-width: none;
		user-select: none;
		-webkit-user-drag: none;
	}
	.pin {
		position: absolute;
		left: 50%;
		top: 50%;
		width: 30px;
		height: 30px;
		margin: -15px 0 0 -15px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--accent);
		color: #fff;
		box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
	}
	.credit {
		position: absolute;
		right: 0;
		bottom: 0;
		padding: 1px 4px;
		border-top-left-radius: 4px;
		background: rgba(255, 255, 255, 0.75);
		color: #333;
		font-size: 9px;
		line-height: 1.3;
	}
</style>
