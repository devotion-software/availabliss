<script lang="ts">
	import type { RequirementTemplate } from '#lib/types';
	import { deleteTemplate, listTemplates, renameTemplate } from '#lib/client/templates';
	import { summarize } from '#lib/client/requirements';
	import { toasts } from '#lib/client/toast.svelte';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Button from '#lib/components/ui/Button.svelte';

	/** Your saved timing templates: rename or delete. New ones are saved from a group's timing. */
	let templates = $state<RequirementTemplate[] | null>(null);
	let renaming = $state<string | null>(null);
	let name = $state('');

	$effect(() => {
		listTemplates()
			.then((list) => (templates = list))
			.catch(() => (templates = []));
	});

	async function saveName(e: SubmitEvent, t: RequirementTemplate) {
		e.preventDefault();
		if (!name.trim()) return;
		try {
			const updated = await renameTemplate(t.id, name.trim());
			templates = templates!.map((x) => (x.id === t.id ? updated : x));
			renaming = null;
		} catch (err) {
			toasts.error(err);
		}
	}

	async function remove(t: RequirementTemplate) {
		try {
			await deleteTemplate(t.id);
			templates = templates!.filter((x) => x.id !== t.id);
			toasts.success(`Deleted “${t.name}”`);
		} catch (err) {
			toasts.error(err);
		}
	}
</script>

<GlassPanel padding="16px" gap={10} aria-labelledby="templates-title">
	<h2 class="eyebrow" id="templates-title">Timing templates</h2>
	{#if templates && templates.length === 0}
		<p class="hint">Save a group's timing as a template (from New group or a group's Timing) to reuse it for new groups.</p>
	{:else if templates}
		<ul>
			{#each templates as t (t.id)}
				<li>
					{#if renaming === t.id}
						<form class="rename" onsubmit={(e) => saveName(e, t)}>
							<TextField label="Name" bind:value={name} maxlength={60} autofocus />
							<Button type="submit" size="sm" variant="secondary">Save</Button>
							<Button size="sm" variant="ghost" onclick={() => (renaming = null)}>Cancel</Button>
						</form>
					{:else}
						<span class="text">
							<span class="name">{t.name}</span>
							<span class="sub">{summarize(t.requirement)}</span>
						</span>
						<IconButton
							icon="pencil"
							label="Rename {t.name}"
							size={36}
							onclick={() => {
								renaming = t.id;
								name = t.name;
							}}
						/>
						<IconButton icon="trash" label="Delete {t.name}" size={36} onclick={() => remove(t)} />
					{/if}
				</li>
			{/each}
		</ul>
	{/if}
</GlassPanel>

<style>
	.hint {
		font-size: 13px;
		color: var(--muted);
	}
	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
	}
	li {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 8px 0;
		border-top: 1px solid var(--line);
	}
	li:first-child {
		border-top: 0;
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.name {
		font-size: 14px;
		font-weight: 600;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.rename {
		flex: 1;
		display: flex;
		align-items: flex-end;
		gap: 6px;
	}
	.rename :global(.field) {
		flex: 1;
	}
</style>
