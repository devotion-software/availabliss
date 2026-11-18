<script lang="ts">
	import type { MessageLocation } from '#lib/types';
	import { formatAccuracy } from '#lib/client/location';
	import Icon from '#lib/components/ui/Icon.svelte';
	import StaticMap from './StaticMap.svelte';

	/** A shared place in a bubble: a small map, its name and address. Tapping it asks where to open it. */
	interface Props {
		location: MessageLocation;
		mine: boolean;
		onopen: () => void;
	}
	let { location, mine, onopen }: Props = $props();

	const title = $derived(location.name || 'Location');
	const detail = $derived(
		location.address || (!location.name && location.accuracy != null ? `Accurate to ${formatAccuracy(location.accuracy)}` : null)
	);
</script>

<button type="button" class="place" class:mine onclick={onopen} aria-label="Location: {title}{detail ? `, ${detail}` : ''}. Open in a maps app">
	<StaticMap lat={location.lat} lng={location.lng} height={130} />
	<span class="info">
		<span class="text">
			<span class="title">{title}</span>
			{#if detail}<span class="detail">{detail}</span>{/if}
		</span>
		<Icon name="external" size={17} strokeWidth={2.2} />
	</span>
</button>

<style>
	.place {
		display: flex;
		flex-direction: column;
		width: 240px;
		max-width: 100%;
		border-radius: 14px;
		overflow: hidden;
		background: var(--surface);
		color: var(--ink);
		text-align: left;
		font: inherit;
		cursor: pointer;
		-webkit-touch-callout: none;
	}
	.place.mine {
		background: color-mix(in srgb, var(--on-accent) 16%, transparent);
		color: var(--on-accent);
	}
	.place:hover {
		filter: brightness(0.97);
	}
	.info {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 8px 10px;
		color: var(--accent-ink);
	}
	.mine .info {
		color: var(--on-accent);
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
		color: var(--ink);
	}
	.mine .text {
		color: var(--on-accent);
	}
	.title {
		font-size: 13.5px;
		font-weight: 600;
		line-height: 1.3;
		overflow-wrap: anywhere;
	}
	.detail {
		font-size: 11.5px;
		opacity: 0.75;
		overflow: hidden;
		text-overflow: ellipsis;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
	}
</style>
