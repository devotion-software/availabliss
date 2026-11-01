<script lang="ts">
	import Avatar from './Avatar.svelte';
	import type { Person } from './avatar';

	interface Props {
		people: readonly Person[];
		max?: number;
		size?: number;
		/** Ring colour: `glass` over panels, `bg` over the page. */
		ringOn?: 'glass' | 'bg';
	}
	let { people, max = 5, size = 22, ringOn = 'glass' }: Props = $props();

	const shown = $derived(people.slice(0, max));
	const extra = $derived(Math.max(0, people.length - max));
</script>

<span class="stack" class:on-bg={ringOn === 'bg'} style:--size="{size}px" aria-hidden="true">
	{#each shown as p (p.id)}
		<Avatar name={p.name} id={p.id} image={p.image} {size} ring />
	{/each}
	{#if extra}<span class="more">+{extra}</span>{/if}
</span>

<style>
	.stack {
		display: inline-flex;
		align-items: center;
		padding-right: calc(var(--size) * 0.32);
	}
	.stack > :global(*) {
		margin-right: calc(var(--size) * -0.32);
	}
	.on-bg :global(.ring) {
		border-color: var(--bg);
	}
	.more {
		height: var(--size);
		min-width: var(--size);
		padding: 0 5px;
		border-radius: 999px;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		background: var(--line-2);
		font-size: calc(var(--size) * 0.42);
		font-weight: 700;
		border: 1.5px solid var(--glass-border);
	}
</style>
