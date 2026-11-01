<script lang="ts">
	/**
	 * The three soft colour orbs every screen floats over. Fixed to the viewport,
	 * blurred, and tinted by the active theme. `layout` varies the arrangement
	 * per screen like the designs do.
	 */
	interface Props {
		layout?: 'a' | 'b' | 'c' | 'd';
	}
	let { layout = 'a' }: Props = $props();
</script>

<div class="orbs {layout}" aria-hidden="true">
	<span class="orb o1"></span>
	<span class="orb o2"></span>
	<span class="orb o3"></span>
</div>

<style>
	.orbs {
		position: fixed;
		inset: 0;
		z-index: 0;
		overflow: hidden;
		pointer-events: none;
	}
	.orb {
		position: absolute;
		border-radius: 50%;
		filter: blur(60px);
		opacity: var(--orb-opacity);
		width: var(--w);
		height: var(--w);
		transition:
			background-color 0.6s ease,
			opacity 0.4s ease;
	}
	.o1 {
		--w: clamp(320px, 42vw, 620px);
		background: var(--orb1);
		left: calc(var(--w) * -0.42);
		top: calc(var(--w) * -0.32);
	}
	.o2 {
		--w: clamp(300px, 38vw, 560px);
		background: var(--orb2);
		right: calc(var(--w) * -0.45);
		top: 28vh;
	}
	.o3 {
		--w: clamp(300px, 36vw, 520px);
		background: var(--orb3);
		left: 28%;
		bottom: calc(var(--w) * -0.4);
	}
	.b .o1 {
		background: var(--orb2);
		left: auto;
		right: calc(var(--w) * -0.38);
	}
	.b .o2 {
		background: var(--orb1);
		right: auto;
		left: calc(var(--w) * -0.48);
		top: 36vh;
	}
	.c .o1 {
		background: var(--orb3);
	}
	.c .o3 {
		background: var(--orb2);
		left: 6%;
	}
	.d .o1 {
		background: var(--orb1);
		top: auto;
		bottom: 30vh;
	}
	.d .o2 {
		background: var(--orb3);
		top: -10vh;
	}
	.d .o3 {
		background: var(--orb2);
	}
	@media (min-width: 900px) {
		.orb {
			filter: blur(80px);
			opacity: calc(var(--orb-opacity) * 0.88);
		}
	}
</style>
