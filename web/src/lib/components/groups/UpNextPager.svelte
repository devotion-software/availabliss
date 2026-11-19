<script lang="ts">
	import type { UpcomingEvent } from '#lib/client/events';
	import type { GroupSummary } from '#lib/types';
	import UpNextCard from './UpNextCard.svelte';

	/**
	 * The next confirmed plans, one Up next card each: swipe (or scroll) sideways between them, with
	 * page dots under them to jump to one (no dots when there's only one). Each card hugs its
	 * content and the pager's height follows the scroll, blending from one card's height to the next,
	 * so a short card leaves no gap and nothing jumps.
	 */
	interface Props {
		events: UpcomingEvent[];
		/** Your groups, for each card's group tile. */
		groups?: GroupSummary[];
	}
	let { events, groups = [] }: Props = $props();
	const groupOf = (id: string) => groups.find((g) => g.id === id);

	let track = $state<HTMLDivElement>();
	let current = $state(0);
	/** Each slide's natural height, by index. */
	let heights = $state<number[]>([]);
	/** Where the scroll is, in cards: 0 on the first, 1.5 halfway between the second and third. */
	let position = $state(0);
	const height = $derived.by(() => {
		if (heights.length < events.length || heights.some((h) => !h)) return 0;
		const p = Math.min(Math.max(position, 0), events.length - 1);
		const i = Math.floor(p);
		const next = Math.min(i + 1, events.length - 1);
		return heights[i] + (heights[next] - heights[i]) * (p - i);
	});

	/** Distance from one slide to the next (a card plus the gap). */
	function stride(el: HTMLDivElement): number {
		const [a, b] = el.children as HTMLCollectionOf<HTMLElement>;
		return b ? b.offsetLeft - a.offsetLeft : el.clientWidth;
	}

	function onScroll() {
		if (!track) return;
		position = track.scrollLeft / stride(track);
		current = Math.min(events.length - 1, Math.max(0, Math.round(position)));
	}

	function show(index: number) {
		if (!track) return;
		const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
		track.scrollTo({ left: index * stride(track), behavior: reduce ? 'auto' : 'smooth' });
	}
</script>

{#if events.length === 1}
	<UpNextCard event={events[0]} group={groupOf(events[0].groupId)} />
{:else}
	<div class="pager" role="region" aria-roledescription="carousel" aria-label="Up next">
		<div class="track" bind:this={track} onscroll={onScroll} style:height={height ? `${height + 44}px` : undefined}>
			{#each events as event, i (event.id)}
				<div class="slide" role="group" aria-roledescription="slide" aria-label="{i + 1} of {events.length}" bind:clientHeight={heights[i]}>
					<UpNextCard {event} group={groupOf(event.groupId)} />
				</div>
			{/each}
		</div>
		<div class="dots">
			{#each events as event, i (event.id)}
				<button
					type="button"
					class="dot"
					class:on={i === current}
					aria-label="Show plan {i + 1} of {events.length}"
					aria-current={i === current ? 'true' : undefined}
					onclick={() => show(i)}
				>
					<span></span>
				</button>
			{/each}
		</div>
	</div>
{/if}

<style>
	.pager {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	/* Bleeds into the page's gutters so the cards' shadows aren't cut off and the next card slides in
	   from the edge; the padding puts each card back in line. */
	.track {
		display: flex;
		gap: var(--gutter);
		overflow-x: auto;
		overscroll-behavior-x: contain;
		scroll-snap-type: x mandatory;
		scroll-padding: 0 var(--gutter);
		scrollbar-width: none;
		/* Follows the scroll (height set inline: the cards' blended height plus the padding). */
		align-items: flex-start;
		box-sizing: border-box;
		padding: 4px var(--gutter) 40px;
		margin: -4px calc(-1 * var(--gutter)) -34px;
	}
	.track::-webkit-scrollbar {
		display: none;
	}
	.slide {
		flex: 0 0 100%;
		scroll-snap-align: start;
		scroll-snap-stop: always;
	}
	.dots {
		display: flex;
		justify-content: center;
	}
	.dot {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-width: 18px;
		height: 24px;
		padding: 0 3px;
		background: none;
		border: 0;
		cursor: pointer;
	}
	.dot span {
		display: block;
		width: 7px;
		height: 7px;
		border-radius: 999px;
		background: color-mix(in srgb, var(--muted) 35%, transparent);
		transition:
			width 0.2s ease,
			background-color 0.2s ease;
	}
	.dot.on span {
		width: 18px;
		background: var(--ink);
	}
	.dot:hover span {
		background: color-mix(in srgb, var(--muted) 70%, transparent);
	}
	.dot.on:hover span {
		background: var(--ink);
	}
	.dot:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: 1px;
		border-radius: 6px;
	}
</style>
