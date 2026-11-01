<script lang="ts">
	import Orbs from '#lib/components/ui/Orbs.svelte';

	let { children } = $props();
</script>

<Orbs layout="c" />
<main class="onboarding">
	{@render children()}
</main>

<style>
	.onboarding {
		position: relative;
		z-index: 1;
		max-width: 520px;
		min-height: 100dvh;
		margin: 0 auto;
		padding: max(28px, env(safe-area-inset-top)) var(--gutter) max(24px, env(safe-area-inset-bottom));
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	@media (min-width: 900px) {
		.onboarding {
			padding-top: 64px;
		}
	}
</style>
