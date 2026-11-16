<script lang="ts">
	import { takePhoto } from '#lib/client/photo';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';

	/** Picks a cover photo for an event that doesn't exist yet; the page uploads it after creating it. */
	interface Props {
		file: File | null;
		disabled?: boolean;
	}
	let { file = $bindable(), disabled = false }: Props = $props();

	let input: HTMLInputElement | undefined = $state();
	const preview = $derived(file ? URL.createObjectURL(file) : null);
	// Let go of the old preview's memory when it changes or the form goes away.
	$effect(() => {
		const url = preview;
		return () => {
			if (url) URL.revokeObjectURL(url);
		};
	});

	function pick(e: Event) {
		const picked = takePhoto(e);
		if (picked) file = picked;
	}
</script>

<div class="cover-field">
	{#if preview}
		<img class="preview" src={preview} alt="The chosen cover" />
		<div class="actions">
			<Button type="button" size="sm" variant="secondary" icon="pencil" {disabled} onclick={() => input?.click()}>Change cover</Button>
			<Button type="button" size="sm" variant="ghost" icon="trash" {disabled} onclick={() => (file = null)}>Remove</Button>
		</div>
	{:else}
		<button type="button" class="add" {disabled} onclick={() => input?.click()}>
			<Icon name="plus" size={18} />
			Add a cover photo (optional)
		</button>
	{/if}
	<input bind:this={input} class="sr-only" type="file" accept="image/*" tabindex="-1" aria-hidden="true" {disabled} onchange={pick} />
</div>

<style>
	.cover-field {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.preview {
		width: 100%;
		aspect-ratio: 2 / 1;
		object-fit: cover;
		border-radius: 18px;
		display: block;
	}
	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}
	.add {
		min-height: 56px;
		border-radius: 18px;
		border: 1.5px dashed var(--line-2);
		background: none;
		color: var(--muted);
		font: inherit;
		font-size: 14px;
		font-weight: 600;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		cursor: pointer;
	}
	.add:hover:not(:disabled) {
		color: var(--ink);
		border-color: var(--ink);
	}
</style>
