<script lang="ts">
	import { goto, invalidate } from '$app/navigation';
	import type { Member } from '#lib/types';
	import { deleteGroup, removeMember, setMemberRole } from '#lib/client/groups';
	import { isAdminRole, memberMap, memberLabel, ROLE_LABEL, groupSubtitle } from '#lib/client/members';
	import { toasts } from '#lib/client/toast.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Avatar from '#lib/components/ui/Avatar.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';
	import InvitePanel from '#lib/components/members/InvitePanel.svelte';
	import MemberActionsSheet from '#lib/components/members/MemberActionsSheet.svelte';

	let { data } = $props();

	const group = $derived(data.group);
	const me = $derived(data.user.id);
	const admin = $derived(isAdminRole(group.role));
	const members = $derived(memberMap(group));
	const ROLE_ORDER = { owner: 0, admin: 1, member: 2 };
	const sorted = $derived(
		[...group.members].sort((a, b) => ROLE_ORDER[a.role] - ROLE_ORDER[b.role] || a.name.localeCompare(b.name))
	);

	let actionsFor = $state<Member | null>(null);
	let actionsOpen = $state(false);
	let removing = $state<Member | null>(null);
	let removeOpen = $state(false);
	let leaveOpen = $state(false);
	let deleteOpen = $state(false);

	const canManage = (m: Member) => admin && m.userId !== me && m.role !== 'owner';

	async function changeRole(m: Member, role: 'admin' | 'member') {
		try {
			await setMemberRole(group.id, m.userId, role);
			toasts.success(role === 'admin' ? `${m.name} is now an admin` : `${m.name} is now a member`);
			await invalidate('app:group');
		} catch (err) {
			toasts.error(err);
		}
	}

	async function remove() {
		if (!removing) return;
		try {
			await removeMember(group.id, removing.userId);
			toasts.success(`${removing.name} was removed`);
			await Promise.all([invalidate('app:group'), invalidate('app:groups')]);
		} catch (err) {
			toasts.error(err);
			throw err;
		}
	}

	async function leave() {
		try {
			await removeMember(group.id, me);
			toasts.success(`You left ${group.name}`);
			await goto('/app', { invalidateAll: true });
		} catch (err) {
			toasts.error(err);
			throw err;
		}
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

<svelte:head><title>Members · {group.name} · Availabliss</title></svelte:head>

<div class="page">
	<PageHeader title="Members" subtitle={groupSubtitle(group)} backHref="/app/groups/{group.id}" backLabel="Back to calendar" />

	<div class="columns">
		<section class="people glass" aria-label="Members">
			{#each sorted as m (m.userId)}
				<div class="person">
					<Avatar name={m.name} id={m.userId} image={m.image} size={38} />
					<div class="who">
						<span class="name">{memberLabel(members, m.userId, me)}</span>
						<span class="sync" class:pending={!m.synced}>
							<span class="dot" aria-hidden="true"></span>
							{m.synced ? 'Synced' : 'Not synced yet'}
						</span>
					</div>
					{#if m.role !== 'member'}
						<StatusPill tone={m.role === 'owner' ? 'indigo' : 'neutral'} icon={m.role === 'owner' ? 'crown' : 'shield'}>
							{ROLE_LABEL[m.role]}
						</StatusPill>
					{/if}
					{#if canManage(m)}
						<IconButton
							icon="more"
							label="Manage {m.name}"
							variant="plain"
							onclick={() => {
								actionsFor = m;
								actionsOpen = true;
							}}
						/>
					{/if}
				</div>
			{/each}
		</section>

		<div class="side">
			{#if admin}
				<InvitePanel groupId={group.id} groupName={group.name} />
			{:else}
				<p class="glass hint">Ask an admin for an invite link to add people.</p>
			{/if}

			<section class="glass danger" aria-label="Leave or delete">
				{#if group.role === 'owner'}
					<p class="small">You own this group, so you can't leave it. Make someone else an admin first if you want them to carry on, or delete it.</p>
					<Button variant="danger" icon="trash" onclick={() => (deleteOpen = true)}>Delete group</Button>
				{:else}
					<Button variant="danger" icon="logout" onclick={() => (leaveOpen = true)}>Leave group</Button>
				{/if}
			</section>
		</div>
	</div>
</div>

{#if actionsFor}
	{@const target = actionsFor}
	<MemberActionsSheet
		bind:open={actionsOpen}
		member={target}
		onrole={(role) => changeRole(target, role)}
		onremove={() => {
			removing = target;
			removeOpen = true;
		}}
	/>
{/if}
<ConfirmSheet
	bind:open={removeOpen}
	title="Remove {removing?.name ?? 'member'}?"
	message="They'll lose access to the group's calendar and chat. You can invite them back later."
	confirmLabel="Remove"
	destructive
	onconfirm={remove}
/>
<ConfirmSheet
	bind:open={leaveOpen}
	title="Leave {group.name}?"
	message="Your busy times stop counting and you won't see the chat. Someone can invite you back."
	confirmLabel="Leave group"
	destructive
	onconfirm={leave}
/>
<ConfirmSheet
	bind:open={deleteOpen}
	title="Delete {group.name}?"
	message="This removes the group, its chat, proposals and events for everyone. It can't be undone."
	confirmLabel="Delete group"
	destructive
	onconfirm={destroy}
/>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: 16px;
		width: 100%;
	}
	.columns {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.people {
		border-radius: 24px;
		padding: 4px 8px 4px 14px;
	}
	.person {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 60px;
		padding: 6px 0;
	}
	.person + .person {
		border-top: 1px solid var(--line);
	}
	.who {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.name {
		font-size: 15px;
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.sync {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		font-size: 12px;
		color: var(--ok-ink);
	}
	.sync.pending {
		color: var(--warn-ink);
	}
	.dot {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: currentColor;
	}
	.side {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.hint {
		border-radius: 20px;
		padding: 14px;
		font-size: 13px;
		color: var(--muted);
	}
	.danger {
		border-radius: 24px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.small {
		font-size: 13px;
		color: var(--muted);
	}
	@media (min-width: 1100px) {
		.columns {
			flex-direction: row;
			align-items: flex-start;
		}
		.people {
			flex: 1.3;
		}
		.side {
			flex: 1;
			position: sticky;
			top: 20px;
		}
	}
</style>
