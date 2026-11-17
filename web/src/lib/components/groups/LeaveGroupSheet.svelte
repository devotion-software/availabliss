<script lang="ts">
	import { invalidate } from '$app/navigation';
	import type { Group, Member } from '#lib/types';
	import { leaveGroup, setMemberRole } from '#lib/client/groups';
	import { isAdminRole, ROLE_LABEL } from '#lib/client/members';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Avatar from '#lib/components/ui/Avatar.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';

	/**
	 * Confirms leaving the group, from Group settings. A group always keeps an owner (and so an
	 * admin), following the server's rules:
	 * - a member, or an admin while another admin stays, just confirms;
	 * - with nobody else an admin, you first pick someone and make them an admin;
	 * - the owner then picks which admin becomes the owner;
	 * - the only member can't leave: they delete the group instead.
	 */
	interface Props {
		open: boolean;
		group: Group;
		me: string;
		/** Left: go back to the groups list. */
		onleft: () => Promise<unknown> | unknown;
		/** Only member, and the owner: close this and open Delete group. */
		ondeleteinstead: () => void;
	}
	let { open = $bindable(), group, me, onleft, ondeleteinstead }: Props = $props();

	const myRole = $derived(group.members.find((m) => m.userId === me)?.role ?? group.role);
	const others = $derived(group.members.filter((m) => m.userId !== me));
	const otherAdmins = $derived(others.filter((m) => isAdminRole(m.role)));
	/** Before an admin can go, someone else has to be an admin. */
	const needsAdmin = $derived(isAdminRole(myRole) && otherAdmins.length === 0);
	const choosesOwner = $derived(myRole === 'owner' && otherAdmins.length > 0);

	let picked = $state<string | null>(null);
	let busy = $state(false);

	// One person to pick: a lone admin is picked for you when choosing the owner. Resets when the
	// sheet opens and when someone's just been made an admin.
	$effect(() => {
		if (!open) return;
		void needsAdmin;
		picked = choosesOwner && otherAdmins.length === 1 ? otherAdmins[0].userId : null;
	});

	const title = $derived(others.length === 0 ? "You're the only member" : needsAdmin ? 'Make someone an admin' : 'Leave group?');
	const description = $derived(
		others.length === 0
			? `Nobody else is in ${group.name}, so there's no one to hand it to. Delete the group instead if you're done with it.`
			: needsAdmin
				? `A group needs an admin, and nobody else in ${group.name} is one. Choose someone to make an admin, then you can leave.`
				: choosesOwner
					? `Choose who becomes the owner. They'll look after ${group.name}, and its bank holidays will follow their region. You'll need a new invite to come back.`
					: "You'll need a new invite to come back."
	);
	const pickable = $derived<Member[]>(needsAdmin ? others : choosesOwner ? otherAdmins : []);

	/** The Members page's Make admin, for whoever is picked. */
	async function makeAdmin() {
		if (!picked) return;
		busy = true;
		try {
			const m = await setMemberRole(group.id, picked, 'admin');
			toasts.success(`${m.name} is now an admin`);
			await invalidate('app:group');
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}

	async function leave() {
		busy = true;
		try {
			await leaveGroup(group.id, choosesOwner ? picked : null);
			open = false;
			await onleft();
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<Sheet bind:open {title} {description} placement="auto" width={440}>
	{#if pickable.length}
		<div class="list" role="radiogroup" aria-label={needsAdmin ? 'Who to make an admin' : 'Who becomes the owner'}>
			{#each pickable as m (m.userId)}
				<button type="button" role="radio" aria-checked={picked === m.userId} class:sel={picked === m.userId} onclick={() => (picked = m.userId)}>
					<Avatar name={m.name} id={m.userId} image={m.image} size={34} />
					<span class="name">{m.name}</span>
					{#if m.role !== 'member'}
						<StatusPill tone={m.role === 'owner' ? 'indigo' : 'neutral'} icon={m.role === 'owner' ? 'crown' : 'shield'}>
							{ROLE_LABEL[m.role]}
						</StatusPill>
					{/if}
					<span class="mark" aria-hidden="true">{#if picked === m.userId}<Icon name="check" size={16} />{/if}</span>
				</button>
			{/each}
		</div>
	{/if}
	{#snippet footer()}
		<Button variant="secondary" onclick={() => (open = false)}>Cancel</Button>
		{#if others.length === 0}
			{#if myRole === 'owner'}
				<Button variant="danger" icon="trash" onclick={ondeleteinstead}>Delete group</Button>
			{/if}
		{:else if needsAdmin}
			<Button variant="accent" icon="shield" loading={busy} disabled={!picked} onclick={makeAdmin}>Make admin</Button>
		{:else}
			<Button variant="danger" icon="logout" loading={busy} disabled={choosesOwner && !picked} onclick={leave}>Leave group</Button>
		{/if}
	{/snippet}
</Sheet>

<style>
	.list {
		display: flex;
		flex-direction: column;
		border-radius: 20px;
		border: 1px solid var(--line);
		padding: 0 12px;
	}
	.list button {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 56px;
		padding: 8px 0;
		background: none;
		border: 0;
		color: inherit;
		font: inherit;
		text-align: left;
		cursor: pointer;
	}
	.list button + button {
		border-top: 1px solid var(--line);
	}
	.name {
		flex: 1;
		min-width: 0;
		font-size: 15px;
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.mark {
		width: 22px;
		height: 22px;
		border-radius: 50%;
		border: 1.5px solid var(--line-2);
		display: grid;
		place-items: center;
		flex-shrink: 0;
	}
	.sel .mark {
		background: var(--accent);
		border-color: var(--accent);
		color: var(--on-accent);
	}
</style>
