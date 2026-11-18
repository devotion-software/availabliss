<script lang="ts">
	import type { MessageLocation } from '#lib/types';
	import { formatAccuracy, searchPlaces, type FoundPlace } from '#lib/client/location';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import StaticMap from './StaticMap.svelte';

	/**
	 * "Location" from the + menu: a map of where you are (the browser asks first) with Send my current
	 * location, or search for a place (OpenStreetMap's Nominatim, on Search) and send that.
	 */
	interface Props {
		open: boolean;
		/** Sends the place; should throw on failure (the sheet stays open). */
		onsend: (location: MessageLocation) => Promise<unknown>;
	}
	let { open = $bindable(), onsend }: Props = $props();

	const uid = $props.id();
	let here = $state<GeolocationPosition | null>(null);
	let locating = $state(false);
	let locateError = $state<string | null>(null);
	let query = $state('');
	let results = $state<FoundPlace[]>([]);
	let searching = $state(false);
	let noResults = $state(false);
	let picked = $state<FoundPlace | null>(null);
	let sending = $state(false);

	// Each time it opens: start fresh and find where you are.
	$effect(() => {
		if (!open) return;
		query = '';
		results = [];
		noResults = false;
		picked = null;
		locateError = null;
		if (!('geolocation' in navigator)) {
			locateError = 'This browser can’t share your location. Search for a place instead.';
			return;
		}
		locating = true;
		let cancelled = false;
		navigator.geolocation.getCurrentPosition(
			(position) => {
				if (cancelled) return;
				here = position;
				locating = false;
			},
			(err) => {
				if (cancelled) return;
				locating = false;
				locateError =
					err.code === err.PERMISSION_DENIED
						? 'Location access is off for this site. Allow it in your browser to send where you are, or search for a place.'
						: 'Couldn’t find where you are. Search for a place instead.';
			},
			{ enableHighAccuracy: true, timeout: 15_000, maximumAge: 60_000 }
		);
		return () => {
			cancelled = true;
		};
	});

	const near = $derived(here ? { lat: here.coords.latitude, lng: here.coords.longitude } : null);
	const myLocation = $derived<MessageLocation | null>(
		here
			? { lat: here.coords.latitude, lng: here.coords.longitude, name: null, address: null, accuracy: Math.round(here.coords.accuracy) }
			: null
	);
	const shown = $derived(picked ? { lat: picked.lat, lng: picked.lng } : near);

	async function search(e: SubmitEvent) {
		e.preventDefault();
		const q = query.trim();
		if (!q || searching) return;
		searching = true;
		picked = null;
		noResults = false;
		try {
			results = await searchPlaces(q, near);
			noResults = results.length === 0;
			if (results.length === 1) picked = results[0];
		} catch (err) {
			toasts.error(err);
		} finally {
			searching = false;
		}
	}

	function clear() {
		query = '';
		results = [];
		noResults = false;
		picked = null;
	}

	async function send(location: MessageLocation) {
		if (sending) return;
		sending = true;
		try {
			await onsend(location);
			open = false;
		} catch {
			// Already toasted; stay open to try again.
		} finally {
			sending = false;
		}
	}
</script>

<Sheet bind:open title="Share a location" width={460}>
	<div class="content">
		<form class="search" role="search" onsubmit={search}>
			<Icon name="search" size={18} />
			<label for="place-{uid}" class="sr-only">Search for a place</label>
			<input id="place-{uid}" type="search" bind:value={query} placeholder="Search for a place" autocomplete="off" enterkeyhint="search" maxlength={200} />
			{#if searching}
				<Spinner size={16} />
			{:else if query}
				<button type="button" class="clear" aria-label="Clear search" onclick={clear}><Icon name="x" size={14} strokeWidth={2.4} /></button>
			{/if}
		</form>

		{#if noResults}
			<p class="note">Nothing found for “{query.trim()}”.</p>
		{:else if results.length && !picked}
			<ul class="results">
				{#each results as place (place.lat + ',' + place.lng + place.name)}
					<li>
						<button type="button" onclick={() => (picked = place)}>
							<Icon name="map-pin" size={20} />
							<span class="text">
								<span class="name">{place.name}</span>
								{#if place.address}<span class="address">{place.address}</span>{/if}
							</span>
						</button>
					</li>
				{/each}
			</ul>
		{/if}

		<div class="preview">
			{#if shown}
				<StaticMap lat={shown.lat} lng={shown.lng} height={200} zoom={picked ? 16 : 15} />
			{:else}
				<p class="note center">
					{#if locating}Finding where you are…{:else}{locateError ?? 'Search for a place to share it.'}{/if}
				</p>
			{/if}
		</div>
		{#if locateError && shown}<p class="note">{locateError}</p>{/if}

		{#if picked}
			<Button variant="accent" icon="map-pin" block loading={sending} onclick={() => picked && send({ lat: picked.lat, lng: picked.lng, name: picked.name, address: picked.address, accuracy: null })}>
				Send {picked.name}
			</Button>
		{/if}
		<Button variant={picked ? 'secondary' : 'solid'} icon="navigation" block disabled={!myLocation || sending} loading={sending && !picked} onclick={() => myLocation && send(myLocation)}>
			Send my current location
		</Button>
		{#if myLocation?.accuracy != null}
			<p class="note center">Your location is accurate to {formatAccuracy(myLocation.accuracy)}</p>
		{/if}
	</div>
</Sheet>

<style>
	.content {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.search {
		display: flex;
		align-items: center;
		gap: 8px;
		height: 46px;
		padding: 0 12px 0 14px;
		border-radius: 14px;
		border: 1px solid var(--line);
		background: var(--surface);
		color: var(--muted);
	}
	.search:focus-within {
		border-color: var(--accent);
		box-shadow: 0 0 0 3px var(--accent-soft);
	}
	.search input {
		flex: 1;
		min-width: 0;
		border: 0;
		background: transparent;
		color: var(--ink);
		font: inherit;
		font-size: 16px;
		outline: none;
	}
	.search input::-webkit-search-cancel-button {
		display: none;
	}
	.clear {
		width: 28px;
		height: 28px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--muted);
	}
	.clear:hover {
		background: var(--accent-soft);
	}
	.results {
		list-style: none;
		margin: 0;
		padding: 0 14px;
		border-radius: 18px;
		background: var(--surface);
	}
	.results li + li {
		border-top: 1px solid var(--line);
	}
	.results button {
		width: 100%;
		min-height: 54px;
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 8px 0;
		text-align: left;
		font: inherit;
		color: var(--accent-ink);
	}
	.results .text {
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.results .name {
		font-size: 15px;
		font-weight: 600;
		color: var(--ink);
	}
	.results .address {
		font-size: 12px;
		color: var(--muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.preview {
		border-radius: 18px;
		overflow: hidden;
		min-height: 200px;
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--accent-soft);
	}
	.note {
		margin: 0;
		font-size: 13px;
		color: var(--muted);
	}
	.center {
		text-align: center;
		padding: 0 16px;
	}
</style>
