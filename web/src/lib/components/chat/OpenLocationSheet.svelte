<script lang="ts">
	import type { MessageLocation } from '#lib/types';
	import { locationLinks } from '#lib/client/location';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';

	/** "Open in": a shared place in Apple Maps, Google Maps or what3words (a bottom sheet on phones). */
	interface Props {
		open: boolean;
		location: MessageLocation;
	}
	let { open = $bindable(), location }: Props = $props();

	const links = $derived(locationLinks(location));
	// Apple Maps first on Apple devices, Google Maps first elsewhere.
	const apple = typeof navigator !== 'undefined' && /iPhone|iPad|Macintosh/.test(navigator.userAgent);
</script>

<Sheet bind:open title="Open in" description={location.name || 'This location'} width={400}>
	<div class="options">
		{#if apple}
			<Button href={links.apple} target="_blank" rel="noopener noreferrer" icon="map" block onclick={() => (open = false)}>Apple Maps</Button>
		{/if}
		<Button href={links.google} target="_blank" rel="noopener noreferrer" icon="map" variant={apple ? 'secondary' : 'solid'} block onclick={() => (open = false)}>
			Google Maps
		</Button>
		{#if !apple}
			<Button href={links.apple} target="_blank" rel="noopener noreferrer" icon="map" variant="secondary" block onclick={() => (open = false)}>Apple Maps</Button>
		{/if}
		<Button href={links.what3words} target="_blank" rel="noopener noreferrer" icon="grid" variant="secondary" block onclick={() => (open = false)}>
			what3words
		</Button>
		<Button variant="secondary" block onclick={() => (open = false)}>Cancel</Button>
	</div>
</Sheet>

<style>
	.options {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
</style>
