<script lang="ts">
	import type { Requirement, RequirementTemplate } from '#lib/types';
	import { createTemplate, listTemplates, matchingTemplate } from '#lib/client/templates';
	import { toasts } from '#lib/client/toast.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';

	/**
	 * "Save as template": names the current timing so it can be reused for new groups.
	 * Once the timing is one of your templates, says so instead of offering to save it again.
	 */
	interface Props {
		requirement: Requirement;
		/** Suggested name, e.g. the group's. */
		suggestion?: string;
		onsaved?: (template: RequirementTemplate) => void;
	}
	let { requirement, suggestion = '', onsaved }: Props = $props();

	let naming = $state(false);
	let name = $state('');
	let busy = $state(false);
	/** Your templates, to tell whether this timing is already one. */
	let templates = $state<RequirementTemplate[]>([]);
	const match = $derived(matchingTemplate(templates, requirement));

	$effect(() => {
		listTemplates()
			.then((list) => (templates = list))
			.catch(() => {});
	});

	function start() {
		name = suggestion;
		naming = true;
	}

	async function save(e: SubmitEvent) {
		e.preventDefault();
		if (!name.trim()) return;
		busy = true;
		try {
			const saved = await createTemplate(name.trim(), $state.snapshot(requirement) as Requirement);
			toasts.success(`Saved “${saved.name}”. Pick it next time you create a group.`);
			naming = false;
			templates = [...templates, saved];
			onsaved?.(saved);
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

{#if naming}
	<form class="naming" onsubmit={save}>
		<TextField label="Template name" bind:value={name} placeholder="e.g. School holiday weekends" maxlength={60} autofocus />
		<div class="actions">
			<Button variant="ghost" size="sm" onclick={() => (naming = false)}>Cancel</Button>
			<Button type="submit" variant="secondary" size="sm" loading={busy} disabled={!name.trim()}>Save template</Button>
		</div>
	</form>
{:else if match}
	<p class="saved"><Icon name="check" size={16} /> Saved as the template “{match.name}”</p>
{:else}
	<Button variant="ghost" size="sm" icon="plus" onclick={start}>Save this timing as a template</Button>
{/if}

<style>
	.naming {
		display: flex;
		flex-direction: column;
		gap: 8px;
		padding: 12px;
		border-radius: 18px;
		background: var(--surface);
		border: 1px solid var(--glass-border);
	}
	.saved {
		display: flex;
		align-items: center;
		gap: 8px;
		margin: 0;
		min-height: 36px;
		font-size: 14px;
		font-weight: 600;
		color: var(--muted);
	}
	.actions {
		display: flex;
		justify-content: flex-end;
		gap: 8px;
	}
</style>
