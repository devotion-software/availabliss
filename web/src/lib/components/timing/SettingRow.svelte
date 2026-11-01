<script lang="ts">
	import type { Snippet } from 'svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';

	/** A labelled on/off row inside a glass list (Bank holidays, School holidays only, …). */
	interface Props {
		name: string;
		sub?: string;
		checked: boolean;
		disabled?: boolean;
		children?: Snippet;
	}
	let { name, sub, checked = $bindable(), disabled = false, children }: Props = $props();
	const uid = $props.id();
</script>

<div class="row">
	<div class="line">
		<div class="text">
			<span class="name" id="row-{uid}">{name}</span>
			{#if sub}<span class="sub">{sub}</span>{/if}
		</div>
		<Toggle bind:checked labelledby="row-{uid}" {disabled} />
	</div>
	{#if children}{@render children()}{/if}
</div>

<style>
	.row {
		display: flex;
		flex-direction: column;
		gap: 10px;
		padding: 8px 0;
	}
	.row + :global(.row) {
		border-top: 1px solid var(--line);
	}
	.line {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 44px;
	}
	.text {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.name {
		font-size: 15px;
		font-weight: 600;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
	}
</style>
