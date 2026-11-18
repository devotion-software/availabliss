<script lang="ts">
	import type { UpcomingEvent } from '#lib/client/events';
	import { useFormat } from '#lib/client/format';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import AddToCalendar from '#lib/components/events/AddToCalendar.svelte';

	/** The next confirmed plan across all groups (Main.dc "Up next"). */
	interface Props {
		event: UpcomingEvent;
	}
	let { event }: Props = $props();
	const fmt = useFormat();
	// Remembers which URL failed, so a new cover gets a fresh try.
	let failed = $state<string | null>(null);
	const cover = $derived(event.image && event.image !== failed ? event.image : null);
	// A confirmed plan opens its proposal; an event added directly opens the group's plans.
	const href = $derived(event.proposalId ? `/app/proposals/${event.proposalId}` : `/app/groups/${event.groupId}/proposals`);
</script>

<section class="next glass" class:has-cover={cover} aria-label="Up next">
	<!-- The whole card opens the plan (stretched link); the group link and calendar buttons sit above it. -->
	<a class="open" {href} aria-label="Open {event.title}"></a>
	{#if cover}<img class="cover" src={cover} alt="" onerror={() => (failed = cover)} />{/if}
	<div class="top">
		<StatusPill tone="mint" icon="check" size="md">Everyone's in</StatusPill>
		<a class="group" href="/app/groups/{event.groupId}">{event.groupName}</a>
	</div>
	<div class="what">
		<h2>{event.allDay ? fmt.current.when(event.start, event.end, true) : fmt.current.dateTime(event.start)}</h2>
		<span class="sub">{[event.title, event.location && `At ${event.location}`].filter(Boolean).join(' · ')}</span>
	</div>
	<div class="actions"><AddToCalendar eventId={event.id} item={event} /></div>
</section>

<style>
	.next {
		position: relative;
		border-radius: 28px;
		padding: 18px;
		display: flex;
		flex-direction: column;
		gap: 14px;
		transition: transform 0.2s ease;
	}
	.next:has(.open:hover) {
		transform: translateY(-1px);
	}
	.next:has(.open:active) {
		transform: scale(0.985);
		transition-duration: 0.1s;
	}
	.open {
		position: absolute;
		inset: 0;
		border-radius: inherit;
		z-index: 1;
		-webkit-tap-highlight-color: transparent;
	}
	.open:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: 2px;
	}
	/* Only the buttons and links take taps; the space around them opens the plan. */
	.actions {
		position: relative;
		z-index: 2;
		pointer-events: none;
	}
	.actions :global(a),
	.actions :global(button) {
		pointer-events: auto;
	}
	/* The cover sits 6px in from the card's edge, its corners following the card's. */
	.has-cover {
		padding-top: 6px;
	}
	.cover {
		display: block;
		width: calc(100% + 24px);
		margin: 0 -12px;
		aspect-ratio: 2 / 1;
		max-height: 240px;
		object-fit: cover;
		border-radius: 22px;
	}
	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
	}
	.group {
		position: relative;
		z-index: 2;
		font-size: 12px;
		font-weight: 500;
		color: var(--muted);
		text-decoration: none;
	}
	.group:hover {
		color: var(--ink);
	}
	.what {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	h2 {
		font-size: 22px;
		font-weight: 650;
		line-height: 1.15;
	}
	.sub {
		font-size: 14px;
		color: var(--muted);
	}
</style>
