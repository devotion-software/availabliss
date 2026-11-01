<script lang="ts">
	interface Props {
		step: number;
		total: number;
	}
	let { step, total }: Props = $props();
</script>

<div class="dots" role="img" aria-label="Step {step} of {total}">
	{#each Array.from({ length: total }, (_, i) => i + 1) as n (n)}
		<span class:on={n === step} class:done={n < step}></span>
	{/each}
</div>

<style>
	.dots {
		display: flex;
		gap: 6px;
	}
	span {
		width: 18px;
		height: 6px;
		border-radius: 3px;
		background: var(--line-2);
		transition:
			width 0.3s var(--ease-spring),
			background-color 0.3s ease;
	}
	.done {
		background: var(--line-3);
	}
	.on {
		width: 30px;
		background: var(--ink);
	}
</style>
