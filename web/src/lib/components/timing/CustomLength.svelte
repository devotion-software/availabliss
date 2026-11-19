<script lang="ts">
	import type { Requirement, RequirementLength } from '#lib/types';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';

	/** Hours / days / span, for a length none of the presets cover. Used by the timing editor and the sign-up wizard. */
	let { requirement = $bindable() }: { requirement: Requirement } = $props();

	const KINDS = [
		{ value: 'duration', label: 'Hours' },
		{ value: 'days', label: 'Days' },
		{ value: 'span', label: 'Span' }
	] as const;
	type Kind = (typeof KINDS)[number]['value'];

	function setKind(kind: Kind) {
		const len: Record<Kind, RequirementLength> = {
			duration: { type: 'duration', minutes: 180 },
			days: { type: 'days', count: 2 },
			span: { type: 'span', startDay: 4, startTime: '18:00', endDay: 6, endTime: '23:59', weeksLater: 0 }
		};
		requirement.length = len[kind];
		requirement.window = kind === 'duration' ? { from: '08:00', to: '23:00' } : null;
	}

	const valueOf = (e: Event) => (e.currentTarget as HTMLInputElement).value;
</script>

<SegmentedControl options={KINDS} value={requirement.length.type} label="Kind of length" variant="soft" size="sm" block onchange={setKind} />
{#if requirement.length.type === 'duration'}
	<TextField
		label="Hours"
		type="number"
		min="0.25"
		max="24"
		step="0.25"
		inputmode="decimal"
		value={String(requirement.length.minutes / 60)}
		onchange={(e) => {
			const h = Number(valueOf(e));
			if (h > 0 && requirement.length.type === 'duration') requirement.length.minutes = Math.round(h * 4) * 15;
		}}
	/>
{:else if requirement.length.type === 'days'}
	<TextField
		label="Consecutive days"
		type="number"
		min="1"
		max="31"
		inputmode="numeric"
		value={String(requirement.length.count)}
		onchange={(e) => {
			const n = Math.round(Number(valueOf(e)));
			if (n >= 1 && n <= 31 && requirement.length.type === 'days') requirement.length.count = n;
		}}
	/>
{/if}
