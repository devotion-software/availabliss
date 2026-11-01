<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLAttributes } from 'svelte/elements';

	interface Props extends HTMLAttributes<HTMLElement> {
		as?: 'div' | 'section' | 'aside' | 'nav' | 'header' | 'form' | 'article';
		/** Corner radius in px (cards 24–28, banners 20). */
		radius?: number;
		/** CSS padding shorthand. */
		padding?: string;
		/** Lay children out as a column with this gap (px). */
		gap?: number;
		children: Snippet;
	}
	let { as = 'div', radius = 24, padding = '16px', gap, class: className = '', children, ...rest }: Props = $props();
</script>

<svelte:element
	this={as}
	class="glass panel {className}"
	class:stack={gap !== undefined}
	style:--r="{radius}px"
	style:--p={padding}
	style:--g="{gap ?? 0}px"
	{...rest}
>
	{@render children()}
</svelte:element>

<style>
	.panel {
		border-radius: var(--r);
		padding: var(--p);
		min-width: 0;
	}
	.stack {
		display: flex;
		flex-direction: column;
		gap: var(--g);
	}
</style>
