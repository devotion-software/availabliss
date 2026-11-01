<script lang="ts">
	/** The Venn mark: three overlapping circles, blended where they meet. */
	interface Props {
		size?: number;
		/** Show the "Availabliss" word mark beside it. */
		wordmark?: boolean;
		/** Gently breathe (landing hero). Off under reduced motion. */
		animated?: boolean;
		label?: string;
	}
	let { size = 34, wordmark = false, animated = false, label }: Props = $props();
</script>

<span class="logo" style:--size="{size}px">
	<svg
		class="venn"
		class:animated
		width={size}
		height={size}
		viewBox="0 0 40 40"
		role={label ? 'img' : undefined}
		aria-label={label}
		aria-hidden={label ? undefined : 'true'}
	>
		<circle class="a" cx="14" cy="15" r="10" fill="#FF9E78" />
		<circle class="b" cx="26" cy="15" r="10" fill="#8F9BFF" />
		<circle class="c" cx="20" cy="25" r="10" fill="#6FD8BB" />
	</svg>
	{#if wordmark}<span class="word">Availabliss</span>{/if}
</span>

<style>
	.logo {
		display: inline-flex;
		align-items: center;
		gap: 10px;
	}
	.word {
		font-family: var(--font-display);
		font-weight: 700;
		letter-spacing: -0.025em;
		font-size: calc(var(--size) * 0.6);
		color: var(--ink);
	}
	circle {
		transform-box: fill-box;
		transform-origin: center;
	}
	.animated .a {
		animation: drift-a 7s ease-in-out infinite;
	}
	.animated .b {
		animation: drift-b 7s ease-in-out infinite;
	}
	.animated .c {
		animation: drift-c 7s ease-in-out infinite;
	}
	@keyframes drift-a {
		50% {
			transform: translate(-1.2px, -0.6px);
		}
	}
	@keyframes drift-b {
		50% {
			transform: translate(1.2px, -0.6px);
		}
	}
	@keyframes drift-c {
		50% {
			transform: translate(0, 1.2px);
		}
	}
</style>
