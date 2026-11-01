<script lang="ts">
	interface Props {
		size?: number;
		label?: string;
	}
	let { size = 18, label = 'Loading' }: Props = $props();
</script>

<span class="spinner" role="status" aria-label={label} style:--size="{size}px"></span>

<style>
	.spinner {
		display: inline-block;
		width: var(--size);
		height: var(--size);
		border-radius: 50%;
		border: 2px solid currentColor;
		border-right-color: transparent;
		opacity: 0.75;
		animation: spin 0.7s linear infinite;
		flex: none;
	}
	@keyframes spin {
		to {
			transform: rotate(360deg);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.spinner {
			animation-duration: 1.6s !important;
			animation-iteration-count: infinite !important;
		}
	}
</style>
