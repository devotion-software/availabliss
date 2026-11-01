<script lang="ts">
	interface Props {
		checked: boolean;
		/** Accessible name (or use `labelledby`). */
		label?: string;
		labelledby?: string;
		disabled?: boolean;
		onchange?: (checked: boolean) => void;
	}
	let { checked = $bindable(), label, labelledby, disabled = false, onchange }: Props = $props();

	function toggle() {
		checked = !checked;
		onchange?.(checked);
	}
</script>

<button
	type="button"
	role="switch"
	class="toggle"
	class:on={checked}
	aria-checked={checked}
	aria-label={label}
	aria-labelledby={labelledby}
	{disabled}
	onclick={toggle}
>
	<span class="knob"></span>
</button>

<style>
	.toggle {
		flex: none;
		width: 52px;
		height: 32px;
		padding: 3px;
		border-radius: 16px;
		background: var(--line-2);
		display: flex;
		transition: background-color 0.2s ease;
	}
	.toggle.on {
		background: var(--accent);
	}
	.toggle:disabled {
		opacity: 0.5;
	}
	.knob {
		width: 26px;
		height: 26px;
		border-radius: 50%;
		background: #ffffff;
		box-shadow: 0 2px 6px rgba(20, 20, 60, 0.25);
		transition: transform 0.2s cubic-bezier(0.3, 1.4, 0.5, 1);
	}
	.on .knob {
		transform: translateX(20px);
	}
</style>
