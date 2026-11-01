<script lang="ts">
	import Icon from './Icon.svelte';

	interface Props {
		label: string;
		value: string;
		options: readonly { value: string; label: string }[];
		/** First, empty option (e.g. "Choose…"). */
		placeholder?: string;
		hideLabel?: boolean;
		disabled?: boolean;
		onchange?: (value: string) => void;
	}
	let { label, value = $bindable(), options, placeholder, hideLabel = false, disabled = false, onchange }: Props = $props();
	const uid = $props.id();
</script>

<div class="field">
	<label for="select-{uid}" class:sr-only={hideLabel}>{label}</label>
	<div class="control">
		<select id="select-{uid}" bind:value {disabled} onchange={() => onchange?.(value)}>
			{#if placeholder !== undefined}<option value="">{placeholder}</option>{/if}
			{#each options as option (option.value)}
				<option value={option.value}>{option.label}</option>
			{/each}
		</select>
		<span class="chev"><Icon name="chevron-down" size={16} /></span>
	</div>
</div>

<style>
	.field {
		display: flex;
		flex-direction: column;
		gap: 6px;
		min-width: 0;
	}
	label {
		font-size: 13px;
		font-weight: 600;
		color: var(--muted);
	}
	.control {
		position: relative;
	}
	select {
		appearance: none;
		width: 100%;
		height: 48px;
		border-radius: var(--radius-control);
		border: 1px solid var(--line-2);
		background: var(--surface);
		padding: 0 40px 0 14px;
		font-size: 15px;
		font-weight: 600;
		color: var(--ink);
		text-overflow: ellipsis;
	}
	select:focus {
		outline: none;
		border-color: var(--accent);
		box-shadow: 0 0 0 3px var(--accent-soft);
	}
	select:disabled {
		opacity: 0.6;
	}
	option {
		background: var(--bg);
		color: var(--ink);
	}
	.chev {
		position: absolute;
		right: 14px;
		top: 50%;
		transform: translateY(-50%);
		color: var(--muted);
		pointer-events: none;
	}
</style>
