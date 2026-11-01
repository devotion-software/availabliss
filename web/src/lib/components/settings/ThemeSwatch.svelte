<script lang="ts">
	interface Props {
		label: string;
		accent: string;
		orbs: readonly [string, string, string];
		selected: boolean;
		tabindex: number;
		onselect: () => void;
		onkeydown: (e: KeyboardEvent) => void;
		ref?: HTMLButtonElement;
	}
	let { label, accent, orbs, selected, tabindex, onselect, onkeydown, ref = $bindable() }: Props = $props();
</script>

<button bind:this={ref} type="button" class="sw" role="radio" aria-checked={selected} aria-label={label} {tabindex} onclick={onselect} {onkeydown}>
	<span class="chip" class:on={selected}>
		<span class="o o1" style:background={orbs[0]}></span>
		<span class="o o2" style:background={orbs[1]}></span>
		<span class="o o3" style:background={orbs[2]}></span>
		<span class="dot" style:background={accent}></span>
	</span>
	<span class="label">{label}</span>
</button>

<style>
	.sw {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 6px;
		min-width: 0;
		transition: transform 0.15s ease;
	}
	.sw:hover {
		transform: translateY(-2px);
	}
	.sw:focus-visible {
		outline: none;
	}
	.sw:focus-visible .chip {
		box-shadow: 0 0 0 3px var(--accent);
	}
	.chip {
		position: relative;
		width: 56px;
		height: 56px;
		border-radius: 18px;
		overflow: hidden;
		background: var(--bg);
		outline: 1px solid var(--line-2);
		outline-offset: 0;
		transition: outline-offset 0.2s ease;
	}
	.chip.on {
		outline: 2.5px solid var(--ink);
		outline-offset: 3px;
	}
	.o {
		position: absolute;
		border-radius: 50%;
		filter: blur(6px);
		opacity: var(--orb-opacity);
	}
	.o1 {
		width: 40px;
		height: 40px;
		left: -8px;
		top: -8px;
	}
	.o2 {
		width: 40px;
		height: 40px;
		right: -10px;
		top: 4px;
	}
	.o3 {
		width: 36px;
		height: 36px;
		left: 4px;
		bottom: -12px;
	}
	.dot {
		position: absolute;
		left: 50%;
		top: 50%;
		width: 20px;
		height: 20px;
		margin: -10px 0 0 -10px;
		border-radius: 50%;
		border: 2px solid rgba(255, 255, 255, 0.85);
	}
	.label {
		font-size: 12px;
		font-weight: 600;
	}
</style>
