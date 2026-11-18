<script lang="ts" module>
	/** The burst's colours: accent, ok, apricot (warn) and a pink a little stronger than the pale tile pink so it reads on glass. */
	export const CONFETTI_COLORS = ['var(--accent)', 'var(--ok)', 'var(--warn)', 'var(--confetti-pink)'];
</script>

<script lang="ts">
	import { onMount } from 'svelte';

	/**
	 * A one-off confetti burst from the top-centre of its positioned parent. Pieces fly up and out, fall with gravity
	 * while spinning, and fade over ~1.6s; then `onend` fires so the parent can unmount it. Decorative only:
	 * non-interactive and hidden from assistive tech. Callers skip it under prefers-reduced-motion.
	 */
	interface Props {
		count?: number;
		duration?: number;
		onend?: () => void;
	}
	let { count = 36, duration = 1600, onend }: Props = $props();

	let host: HTMLElement | undefined = $state();

	const rand = (min: number, max: number) => min + Math.random() * (max - min);

	onMount(() => {
		if (!host) return;
		const animations: Animation[] = [];
		const steps = 10;
		for (let i = 0; i < count; i++) {
			const el = document.createElement('span');
			const round = i % 3 === 0;
			el.className = round ? 'piece dot' : 'piece';
			el.style.background = CONFETTI_COLORS[i % CONFETTI_COLORS.length];
			// Throwaway pieces in a host element Svelte renders empty; they go when the component unmounts.
			// eslint-disable-next-line svelte/no-dom-manipulating
			host.appendChild(el);

			// Launch upward in a ~140° fan, then fall under gravity (px, seconds).
			const angle = rand(-160, -20) * (Math.PI / 180);
			const speed = rand(260, 520);
			const vx = Math.cos(angle) * speed;
			const vy = Math.sin(angle) * speed;
			const g = rand(700, 900);
			const spin = rand(-720, 720);
			const t = duration / 1000;
			const frames: Keyframe[] = [];
			for (let s = 0; s <= steps; s++) {
				const k = s / steps;
				const tt = k * t;
				// A little air drag on the horizontal so pieces drift rather than fly off straight.
				const x = vx * tt * (1 - 0.35 * k);
				const y = vy * tt + 0.5 * g * tt * tt;
				frames.push({
					transform: `translate(${x.toFixed(1)}px, ${y.toFixed(1)}px) rotate(${(spin * k).toFixed(0)}deg) rotateX(${(spin * 1.5 * k).toFixed(0)}deg)`,
					opacity: k < 0.6 ? 1 : 1 - (k - 0.6) / 0.4
				});
			}
			const a = el.animate(frames, {
				duration: duration * rand(0.85, 1.05),
				delay: rand(0, 60),
				easing: 'linear',
				fill: 'both'
			});
			animations.push(a);
		}
		let done = false;
		Promise.all(animations.map((a) => a.finished))
			.catch(() => {})
			.then(() => {
				if (!done) onend?.();
			});
		return () => {
			done = true;
			animations.forEach((a) => a.cancel());
		};
	});
</script>

<div class="confetti" bind:this={host} aria-hidden="true"></div>

<style>
	.confetti {
		--confetti-pink: #f47ab8;
		position: absolute;
		left: 50%;
		top: 14px;
		width: 0;
		height: 0;
		pointer-events: none;
		z-index: 5;
		perspective: 400px;
	}
	.confetti :global(.piece) {
		position: absolute;
		left: -3px;
		top: -5px;
		width: 6px;
		height: 10px;
		border-radius: 1.5px;
		will-change: transform, opacity;
	}
	.confetti :global(.piece.dot) {
		height: 6px;
		top: -3px;
		border-radius: 50%;
	}
</style>
