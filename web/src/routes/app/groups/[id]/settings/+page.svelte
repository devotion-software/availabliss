<script lang="ts">
	import { goto, invalidate } from '$app/navigation';
	import type { TileColor } from '#lib/types';
	import { deleteGroup, updateGroup } from '#lib/client/groups';
	import { groupSubtitle, isAdminRole } from '#lib/client/members';
	import { toasts } from '#lib/client/toast.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';
	import GroupPhotoPanel from '#lib/components/groups/GroupPhotoPanel.svelte';
	import GroupColorPicker from '#lib/components/groups/GroupColorPicker.svelte';
	import LeaveGroupSheet from '#lib/components/groups/LeaveGroupSheet.svelte';

	/**
	 * Group settings, from the group's More menu (and the sidebar): its photo, name and colour,
	 * Archive / Unarchive, Leave group and Delete group. Everyone can open it and leave; only the
	 * owner and admins can change things (the server's rules), and only the owner can delete the
	 * group. Each part is its own card. Admins can also have the group archive itself once its
	 * event has passed.
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
	// The switch moves straight away; it follows the saved setting (and goes back if saving fails).
	let autoArchive = $state(false);
	let savingAutoArchive = $state(false);
	$effect.pre(() => {
		autoArchive = group.autoArchive ?? false;
	});
	let deleteOpen = $state(false);
	let leaveOpen = $state(false);

	async function left() {
		toasts.success(`You left ${group.name}`);
		await goto('/app', { invalidateAll: true });
	}

	async function save(patch: { name?: string; color?: TileColor; archived?: boolean; autoArchive?: boolean }, done: string): Promise<boolean> {
		try {
			await updateGroup(group.id, patch);
			// The group's own details and the groups list (sidebar and home) both show it.
			await Promise.all([invalidate('app:group'), invalidate('app:groups')]);
			toasts.success(done);
			return true;
		} catch (err) {
			toasts.error(err);
			return false;
		}
	}

	async function setAutoArchive(on: boolean) {
		savingAutoArchive = true;
		if (!(await save({ autoArchive: on }, on ? 'Automatic archiving on' : 'Automatic archiving off'))) autoArchive = group.autoArchive ?? false;
		savingAutoArchive = false;
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
				<div class="auto">
					<div class="text">
						<h3 id="auto-archive-title">Archive automatically</h3>
						<p class="small">Once its last event has passed and nothing else is planned. A new plan brings it back.</p>
					</div>
					<Toggle bind:checked={autoArchive} labelledby="auto-archive-title" disabled={savingAutoArchive} onchange={setAutoArchive} />
				</div>
			</section>
		{/if}

		<section class="glass card" aria-labelledby="leave-title">
			<div class="text">
				<h2 id="leave-title">Leave group</h2>
				<p class="small">
					{isOwner
						? "You'll choose an admin to become the owner. You'll need a new invite to come back."
						: "You'll need a new invite to come back."}
				</p>
			</div>
			<Button variant="danger" icon="logout" onclick={() => (leaveOpen = true)}>Leave group</Button>
		</section>

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

<LeaveGroupSheet
	bind:open={leaveOpen}
	{group}
	me={data.user.id}
	onleft={left}
	ondeleteinstead={() => {
		leaveOpen = false;
		deleteOpen = true;
	}}
/>
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
	.auto {
		display: flex;
		align-items: center;
		gap: 12px;
		padding-top: 14px;
		border-top: 1px solid var(--line);
	}
	.auto .text {
		flex: 1;
		min-width: 0;
	}
	h3 {
		font-size: 15px;
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
