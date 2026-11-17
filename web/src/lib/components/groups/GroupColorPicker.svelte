<script lang="ts">
	import { TILE_COLORS, type TileColor } from '#lib/types';

	/** The group tile colours (`t1` … `t5`) as swatches, the chosen one ringed. Used when creating a group and in its settings. */
	interface Props {
		value: TileColor;
		onchange: (color: TileColor) => void;
		disabled?: boolean;
	}
	let { value, onchange, disabled = false }: Props = $props();
</script>

<div class="colors" role="radiogroup" aria-label="Tile colour">
	{#each TILE_COLORS as c (c)}
		<button
			type="button"
			role="radio"
			aria-checked={value === c}
			aria-label="Colour {c.slice(1)}"
			class:on={value === c}
			style:background="var(--{c})"
			{disabled}
			onclick={() => onchange(c)}
		></button>
	{/each}
</div>

<style>
	.colors {
		display: flex;
		gap: 10px;
	}
	.colors button {
		width: 44px;
		height: 44px;
		border-radius: 14px;
		border: 1px solid var(--glass-border);
		transition: transform 0.15s ease;
	}
	.colors button:hover:not(:disabled) {
		transform: translateY(-2px);
	}
	.colors button:disabled {
		opacity: 0.6;
		cursor: default;
	}
	.colors button.on {
		outline: 2.5px solid var(--ink);
		outline-offset: 3px;
	}
</style>
