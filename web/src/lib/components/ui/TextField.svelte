<script lang="ts">
	import type { HTMLInputAttributes } from 'svelte/elements';

	interface Props extends Omit<HTMLInputAttributes, 'value'> {
		label: string;
		value?: string;
		/** Keep the label for screen readers only. */
		hideLabel?: boolean;
		hint?: string;
		error?: string | null;
		multiline?: boolean;
		rows?: number;
	}
	let {
		label,
		value = $bindable(''),
		hideLabel = false,
		hint,
		error,
		multiline = false,
		rows = 3,
		id: idProp,
		class: className = '',
		...rest
	}: Props = $props();

	const uid = $props.id();
	const id = $derived(idProp ?? `field-${uid}`);
	const describedBy = $derived([error && `${id}-error`, hint && `${id}-hint`].filter(Boolean).join(' ') || undefined);
</script>

<div class="field {className}">
	<label for={id} class:sr-only={hideLabel}>{label}</label>
	{#if multiline}
		<textarea
			{id}
			bind:value
			{rows}
			aria-invalid={error ? 'true' : undefined}
			aria-describedby={describedBy}
			placeholder={rest.placeholder}
			maxlength={rest.maxlength}
			required={rest.required}
			disabled={rest.disabled}
		></textarea>
	{:else}
		<input {id} bind:value aria-invalid={error ? 'true' : undefined} aria-describedby={describedBy} {...rest} />
	{/if}
	{#if error}
		<span class="error" id="{id}-error" role="alert">{error}</span>
	{:else if hint}
		<span class="hint" id="{id}-hint">{hint}</span>
	{/if}
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
	input,
	textarea {
		width: 100%;
		min-height: 48px;
		border-radius: var(--radius-control);
		border: 1px solid var(--line-2);
		background: var(--surface);
		padding: 0 14px;
		font-size: 16px;
		color: var(--ink);
		transition:
			border-color 0.2s ease,
			box-shadow 0.2s ease;
	}
	textarea {
		padding: 12px 14px;
		resize: vertical;
		line-height: 1.45;
	}
	input:focus,
	textarea:focus {
		outline: none;
		border-color: var(--accent);
		box-shadow: 0 0 0 3px var(--accent-soft);
	}
	input[aria-invalid='true'],
	textarea[aria-invalid='true'] {
		border-color: var(--warn);
	}
	input[type='date'],
	input[type='time'] {
		font-weight: 600;
		font-size: 15px;
	}
	.hint,
	.error {
		font-size: 12px;
		color: var(--muted);
	}
	.error {
		color: var(--warn-ink);
		font-weight: 600;
	}
</style>
