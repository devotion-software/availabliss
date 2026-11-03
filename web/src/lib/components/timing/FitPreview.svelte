<script lang="ts">
	import type { Requirement } from '#lib/types';
	import { fitCountLabel, summarize } from '#lib/client/requirements';
	import { createPreview } from '#lib/client/requirement-preview.svelte';
	import { formatDayKey } from '#lib/client/format';
	import Spinner from '#lib/components/ui/Spinner.svelte';

	/** "13 weekends fit this timing between 2 Nov and 1 Feb", recomputed (debounced) as the timing changes. */
	interface Props {
		requirement: Requirement;
		timeZone: string;
	}
	let { requirement, timeZone }: Props = $props();

	const live = createPreview(() => requirement, () => timeZone);
	const preview = $derived(live.preview);

	const short = (key: string) => formatDayKey(key).replace(/^\w+ /, '');
</script>

<div class="fit" aria-live="polite">
	<strong>{preview ? fitCountLabel(requirement, preview.count) : summarize(requirement).split(' · ')[0]}</strong>
	<span>{preview ? `fit this timing between ${short(preview.from)} and ${short(preview.to)}` : summarize(requirement)}</span>
	{#if live.loading}<span class="spin"><Spinner size={14} label="Counting" /></span>{/if}
</div>

<style>
	.fit {
		position: relative;
		display: flex;
		flex-direction: column;
		gap: 2px;
		padding-right: 22px;
	}
	strong {
		font-family: var(--font-display);
		font-size: 18px;
		font-weight: 700;
	}
	span {
		font-size: 12px;
		color: var(--muted);
	}
	.spin {
		position: absolute;
		right: 0;
		top: 4px;
	}
</style>
