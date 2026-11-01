<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		value: number;
		total: number;
		size?: number;
		stroke?: number;
		tone?: 'accent' | 'warn' | 'ok';
		/** Content in the middle of the ring (e.g. "4/6"). */
		children?: Snippet;
		/** Accessible description, e.g. "4 of 6 synced". */
		label?: string;
	}
	let { value, total, size = 16, stroke = 3, tone = 'accent', children, label }: Props = $props();

	const r = $derived((size - stroke) / 2);
	const c = $derived(2 * Math.PI * r);
	const fraction = $derived(total > 0 ? Math.min(1, value / total) : 0);
</script>

<span
	class="ring"
	style:--size="{size}px"
	role={label ? 'img' : undefined}
	aria-label={label}
	aria-hidden={label ? undefined : 'true'}
>
	<svg width={size} height={size} viewBox="0 0 {size} {size}">
		<circle cx={size / 2} cy={size / 2} {r} fill="none" stroke="var(--line-2)" stroke-width={stroke} />
		<circle
			class="arc"
			cx={size / 2}
			cy={size / 2}
			{r}
			fill="none"
			stroke="var(--{tone})"
			stroke-width={stroke}
			stroke-linecap="round"
			stroke-dasharray="{c * fraction} {c}"
			transform="rotate(-90 {size / 2} {size / 2})"
		/>
	</svg>
	{#if children}<span class="center">{@render children()}</span>{/if}
</span>

<style>
	.ring {
		position: relative;
		flex: none;
		width: var(--size);
		height: var(--size);
		display: inline-block;
	}
	.arc {
		transition: stroke-dasharray 0.6s var(--ease-spring);
	}
	.center {
		position: absolute;
		inset: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		line-height: 1;
	}
</style>
