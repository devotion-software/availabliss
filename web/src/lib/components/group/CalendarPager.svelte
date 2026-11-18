<script lang="ts">
	import type { Snippet } from 'svelte';
	import { untrack } from 'svelte';
	import { fly } from 'svelte/transition';
	import { prefersReducedMotion } from 'svelte/motion';

	/**
	 * One page per month / week of a group calendar: a new page slides in from the side it's coming
	 * from, and on a touch screen a sideways swipe (the page follows the finger) asks for the
	 * previous / next one.
	 */
	interface Props {
		/** Changes when the month or week shown does, e.g. "month:2026-11-01". Same view, later key = forwards. */
		pageKey: string;
		/** Draw the calendar in a glass box (the week view on wider phones). */
		boxed: boolean;
		onstep: (direction: 1 | -1) => void;
		children: Snippet;
	}
	let { pageKey, boxed, onstep, children }: Props = $props();

	let pageDir = $state<1 | -1>(1);
	let lastPageKey = untrack(() => pageKey);
	$effect.pre(() => {
		const key = pageKey;
		untrack(() => {
			if (key.split(':')[0] === lastPageKey.split(':')[0] && key !== lastPageKey) pageDir = key > lastPageKey ? 1 : -1;
			lastPageKey = key;
		});
	});
	const pageIn = (node: Element) =>
		fly(node, { x: prefersReducedMotion.current ? 0 : pageDir * 48, duration: prefersReducedMotion.current ? 0 : 220 });

	let touch: { x: number; y: number } | null = null;
	// The last two moves, for the flick speed.
	let moves: { x: number; t: number }[] = [];
	let dragX = $state(0);
	function touchStart(e: TouchEvent) {
		touch = e.touches.length === 1 ? { x: e.touches[0].clientX, y: e.touches[0].clientY } : null;
		moves = [];
	}
	function touchMove(e: TouchEvent) {
		if (!touch) return;
		const dx = e.touches[0].clientX - touch.x;
		const dy = e.touches[0].clientY - touch.y;
		dragX = Math.abs(dx) > Math.abs(dy) * 1.5 ? dx : 0;
		moves = [...moves.slice(-1), { x: e.touches[0].clientX, t: e.timeStamp }];
	}
	function touchEnd(e: TouchEvent) {
		if (!touch) return;
		const dx = e.changedTouches[0].clientX - touch.x;
		const dy = e.changedTouches[0].clientY - touch.y;
		touch = null;
		dragX = 0;
		const [a, b] = moves;
		const speed = a && b && b.t > a.t ? (b.x - a.x) / (b.t - a.t) : 0; // px per ms
		const sideways = Math.abs(dx) > Math.abs(dy) * 1.5;
		// A long enough drag, or a quick flick.
		if (sideways && (Math.abs(dx) > 60 || (Math.abs(speed) > 0.6 && Math.abs(dx) > 20))) onstep(dx < 0 ? 1 : -1);
	}
</script>

<div
	class="cal-body"
	role="group"
	aria-label="Calendar"
	class:boxed
	class:dragging={dragX !== 0}
	style:transform={dragX ? `translateX(${dragX}px)` : undefined}
	ontouchstart={touchStart}
	ontouchmove={touchMove}
	ontouchend={touchEnd}
	ontouchcancel={() => ((touch = null), (dragX = 0))}
>
	{#key pageKey}
		<div class="page" in:pageIn>
			{@render children()}
		</div>
	{/key}
</div>

<style>
	.cal-body {
		transition: transform 0.25s ease;
		touch-action: pan-y;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.page {
		display: flex;
		flex-wrap: wrap;
		flex-direction: column;
		align-items: flex-start;
		gap: 10px;
	}
	.cal-body.dragging {
		transition: none;
	}
	.cal-body.boxed {
		background: var(--glass);
		-webkit-backdrop-filter: var(--blur);
		backdrop-filter: var(--blur);
		border: 1px solid var(--glass-border);
		box-shadow: var(--glass-shadow);
		border-radius: 22px;
		padding: 12px;
	}
	@media (min-width: 900px) {
		.cal-body.boxed {
			background: none;
			-webkit-backdrop-filter: none;
			backdrop-filter: none;
			border: 0;
			box-shadow: none;
			border-radius: 0;
			padding: 0;
		}
	}
</style>
