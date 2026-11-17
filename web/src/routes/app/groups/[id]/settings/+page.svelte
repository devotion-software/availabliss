<script lang="ts">
	import { goto, invalidate } from '$app/navigation';
	import type { TileColor } from '#lib/types';
	import { deleteGroup, updateGroup } from '#lib/client/groups';
	import { groupSubtitle, isAdminRole } from '#lib/client/members';
	import { toasts } from '#lib/client/toast.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';
	import GroupPhotoPanel from '#lib/components/groups/GroupPhotoPanel.svelte';
	import GroupColorPicker from '#lib/components/groups/GroupColorPicker.svelte';

	/**
	 * Group settings, from the group's More menu (and the sidebar): its photo, name and colour,
	 * Archive / Unarchive, and Delete group. Everyone can open it; only the owner and admins can
	 * change things (the server's rules), and only the owner can delete the group. Each part is
	 * its own card, so further settings slot in as more cards.
	 */
	let { data } = $props();

	const group = $derived(data.group);
	const canEdit = $derived(isAdminRole(group.role));
	const isOwner = $derived(group.role === 'owner');

	// The name being edited. Follows the saved name (here or a rename by someone else) until you type.
	let name = $state('');
	let savedName = $state('');
	$effect.pre(() => {
		if (name === savedName) name = group.name;
		savedName = group.name;
	});
	const nameChanged = $derived(name.trim() !== '' && name.trim() !== group.name);

	let savingName = $state(false);
	let archiving = $state(false);
	let deleteOpen = $state(false);

	async function save(patch: { name?: string; color?: TileColor; archived?: boolean }, done: string) {
		try {
			await updateGroup(group.id, patch);
			// The group's own details and the groups list (sidebar and home) both show it.
			await Promise.all([invalidate('app:group'), invalidate('app:groups')]);
			toasts.success(done);
		} catch (err) {
			toasts.error(err);
		}
	}

	async function saveName(e?: Event) {
		e?.preventDefault();
		if (!nameChanged || savingName) return;
		savingName = true;
		await save({ name: name.trim() }, 'Name updated');
		savingName = false;
	}

	async function toggleArchived() {
		archiving = true;
		const archive = !group.archived;
		await save({ archived: archive }, archive ? 'Group archived' : 'Group unarchived');
		archiving = false;
	}

	async function destroy() {
		try {
			await deleteGroup(group.id);
			toasts.success(`${group.name} was deleted`);
			await invalidate('app:groups');
			await goto('/app');
		} catch (err) {
			toasts.error(err);
			throw err;
		}
	}
</script>

<svelte:head><title>Group settings · {group.name} · Availabliss</title></svelte:head>

<div class="page">
	<PageHeader
		title="Group settings"
		subtitle={groupSubtitle(group)}
		backHref="/app/groups/{group.id}"
		backLabel="Back to calendar"
		crumbs={[{ label: 'Groups', href: '/app' }, { label: group.name, href: `/app/groups/${group.id}` }, { label: 'Settings' }]}
	/>

	<div class="cards">
		{#if canEdit}
			<GroupPhotoPanel {group} />
		{:else}
			<section class="glass card row" aria-label="Group photo">
				<GroupTile monogram={group.monogram} color={group.color} image={group.image} size={56} />
				<p class="small">Only the group's owner and admins can change the photo, name and colour.</p>
			</section>
		{/if}

		<section class="glass card" aria-labelledby="details-title">
			<h2 id="details-title" class="sr-only">Name and colour</h2>
			{#if canEdit}
				<form class="name" onsubmit={saveName}>
					<TextField label="Name" bind:value={name} maxlength={80} />
					{#if nameChanged}
						<Button type="submit" variant="accent" loading={savingName}>Save</Button>
					{/if}
				</form>
			{:else}
				<div class="field">
					<span class="eyebrow">Name</span>
					<span class="value">{group.name}</span>
				</div>
			{/if}
			<div class="field">
				<span class="eyebrow">Colour</span>
				<GroupColorPicker
					value={group.color}
					disabled={!canEdit}
					onchange={(c) => {
						if (c !== group.color) save({ color: c }, 'Colour updated');
					}}
				/>
			</div>
		</section>

		{#if canEdit}
			<section class="glass card" aria-labelledby="archive-title">
				<div class="text">
					<h2 id="archive-title">{group.archived ? 'Archived' : 'Archive'}</h2>
					<p class="small">
						{group.archived
							? "It's under Archived on everyone's groups list. Unarchive it to bring it back."
							: "Moves it to Archived at the bottom of everyone's groups list. Nothing is deleted, and you can unarchive it any time."}
					</p>
				</div>
				<Button variant="secondary" icon="archive" loading={archiving} onclick={toggleArchived}>
					{group.archived ? 'Unarchive group' : 'Archive group'}
				</Button>
			</section>
		{/if}

		{#if isOwner}
			<section class="glass card" aria-labelledby="delete-title">
				<div class="text">
					<h2 id="delete-title">Delete group</h2>
					<p class="small">Deletes it for everyone in it, with its chat, plans and photos.</p>
				</div>
				<Button variant="danger" icon="trash" onclick={() => (deleteOpen = true)}>Delete group</Button>
			</section>
		{/if}
	</div>
</div>

<ConfirmSheet
	bind:open={deleteOpen}
	title="Delete group?"
	message="{group.name} will be deleted for everyone in it, with its chat, plans and photos. This can't be undone."
	confirmLabel="Delete group"
	destructive
	placement="auto"
	onconfirm={destroy}
/>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: 16px;
		width: 100%;
	}
	.cards {
		display: flex;
		flex-direction: column;
		gap: 14px;
		max-width: 640px;
	}
	.card {
		border-radius: 24px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.card.row {
		flex-direction: row;
		align-items: center;
	}
	.name {
		display: flex;
		align-items: flex-end;
		gap: 8px;
	}
	.name :global(.field) {
		flex: 1;
	}
	.field {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.value {
		font-size: 16px;
		font-weight: 600;
	}
	.text {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	h2 {
		font-size: 16px;
		font-weight: 650;
	}
	.small {
		font-size: 13px;
		color: var(--muted);
	}
	.card :global(.btn) {
		align-self: flex-start;
	}
</style>
