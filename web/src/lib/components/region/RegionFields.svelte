<script lang="ts">
	import { listCountries, listSchoolAuthorities, listSubdivisions, timeZones, type Option } from '#lib/client/regions';
	import { toasts } from '#lib/client/toast.svelte';
	import Select from '#lib/components/ui/Select.svelte';

	import type { RegionValue } from './region';

	/** Country, region, school-holiday authority and time zone, each loaded for the chosen country. */
	interface Props {
		value: RegionValue;
	}
	let { value = $bindable() }: Props = $props();

	let countries = $state<Option[]>([]);
	let subdivisions = $state<Option[]>([]);
	let authorities = $state<Option[]>([]);

	const zones = $derived(timeZones(value.timezone).map((z) => ({ value: z, label: z.replace(/_/g, ' ') })));

	$effect(() => {
		listCountries()
			.then((c) => (countries = c))
			.catch(toasts.error);
	});

	// Reload the per-country lists whenever the country changes.
	$effect(() => {
		const country = value.country;
		if (!country) {
			subdivisions = [];
			authorities = [];
			return;
		}
		let cancelled = false;
		Promise.all([listSubdivisions(country), listSchoolAuthorities(country)])
			.then(([s, a]) => {
				if (cancelled) return;
				subdivisions = s;
				authorities = a.map((x) => ({ code: x.id, name: x.name }));
			})
			.catch((err) => !cancelled && toasts.error(err));
		return () => {
			cancelled = true;
		};
	});

	function onCountry() {
		value.subdivision = '';
		value.schoolAuthority = '';
	}

	const opts = (list: Option[]) => list.map((o) => ({ value: o.code, label: o.name }));
	// Keep a saved value selectable before its list has loaded.
	const withCurrent = (list: { value: string; label: string }[], current: string) =>
		current && !list.some((o) => o.value === current) ? [{ value: current, label: current }, ...list] : list;
</script>

<div class="region">
	<Select label="Country" bind:value={value.country} options={withCurrent(opts(countries), value.country)} placeholder="Choose a country" onchange={onCountry} />
	{#if subdivisions.length}
		<Select label="Region" bind:value={value.subdivision} options={withCurrent(opts(subdivisions), value.subdivision)} placeholder="Whole country" />
	{/if}
	{#if authorities.length}
		<Select
			label="School holidays from"
			bind:value={value.schoolAuthority}
			options={withCurrent(opts(authorities), value.schoolAuthority)}
			placeholder="No school holidays"
		/>
	{/if}
	<Select label="Time zone" bind:value={value.timezone} options={zones} />
</div>

<style>
	.region {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
</style>
