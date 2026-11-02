<script lang="ts">
	import type { Requirement, RequirementTemplate } from '#lib/types';
	import { createTemplate } from '#lib/client/templates';
	import { toasts } from '#lib/client/toast.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';

	/** "Save as template": names the current timing so it can be reused for new groups. */
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
	.actions {
		display: flex;
		justify-content: flex-end;
		gap: 8px;
	}
</style>
