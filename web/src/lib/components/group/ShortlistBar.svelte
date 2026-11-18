<script lang="ts">
	import { fly } from 'svelte/transition';
	import { prefersReducedMotion } from 'svelte/motion';
	import Button from '#lib/components/ui/Button.svelte';
	import DatePollSheet from './DatePollSheet.svelte';
	import type { Shortlist } from './shortlist.svelte';

	/**
	 * "3 picked · Ask the group": floats above the tab bar while times are shortlisted, and opens
	 * the date poll sheet for them.
	 */
	interface Props {
		shortlist: Shortlist;
		groupId: string;
	}
	let { shortlist, groupId }: Props = $props();

	let askOpen = $state(false);
</script>

{#if shortlist.slots.length}
	<div class="shortlist glass" role="region" aria-label="Shortlist" transition:fly={{ y: prefersReducedMotion.current ? 0 : 24, duration: 200 }}>
		<span class="picked" aria-live="polite">
			<strong>{shortlist.slots.length} picked</strong>
			<span>{shortlist.slots.length < 2 ? 'Pick another time' : 'Let the group vote'}</span>
		</span>
		<button type="button" class="clear" onclick={shortlist.clear}>Clear</button>
		<Button variant="accent" size="sm" icon="poll" disabled={shortlist.slots.length < 2} onclick={() => (askOpen = true)}>Ask the group</Button>
	</div>
{/if}

<DatePollSheet bind:open={askOpen} {groupId} slots={shortlist.slots} onremove={shortlist.remove} onposted={shortlist.clear} />

<style>
	.shortlist {
		position: fixed;
		left: 50%;
		transform: translateX(-50%);
		bottom: calc(var(--tabbar-space) + env(safe-area-inset-bottom));
		z-index: 40;
		width: min(calc(100vw - 32px), 440px);
		border-radius: 22px;
		padding: 8px 8px 8px 16px;
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.picked {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.picked strong {
		font-size: 15px;
		font-weight: 650;
	}
	.picked span {
		font-size: 12px;
		color: var(--muted);
	}
	.clear {
		min-height: 44px;
		padding: 0 6px;
		font-size: 13px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	@media (min-width: 900px) {
		.shortlist {
			bottom: 24px;
		}
	}
</style>
