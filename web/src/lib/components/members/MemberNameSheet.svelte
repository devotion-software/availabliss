<script lang="ts">
	import type { Member } from '#lib/types';
	import { setMemberName } from '#lib/client/groups';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';

	/**
	 * The name someone goes by in this group: your own, or (admins) anyone's. Blank, or "Use … account
	 * name", goes back to their account name.
	 */
	interface Props {
		open: boolean;
		groupId: string;
		groupName: string;
		member: Member;
		/** Whether it's your own name. */
		isMe: boolean;
		/** Your account name, when it's your own. */
		myAccountName?: string;
		onsaved: (member: Member) => void;
	}
	let { open = $bindable(), groupId, groupName, member, isMe, myAccountName, onsaved }: Props = $props();

	let name = $state('');
	let saving = $state(false);
	$effect.pre(() => {
		if (open) name = member.name;
	});

	const trimmed = $derived(name.trim());
	// Blank, or the account name itself, means going by the account name.
	const toSave = $derived(
		!trimmed || (isMe && trimmed === myAccountName) || (member.nameInGroup === null && trimmed === member.name) ? null : trimmed
	);
	const changed = $derived(toSave !== member.nameInGroup);
	const accountLabel = $derived(isMe ? (myAccountName ? `Use ${myAccountName}` : 'Use my account name') : 'Use their account name');

	async function save(value: string | null, e?: Event) {
		e?.preventDefault();
		if (saving) return;
		if (value === member.nameInGroup) return void (open = false);
		saving = true;
		try {
			const saved = await setMemberName(groupId, member.userId, value);
			open = false;
			onsaved(saved);
			toasts.success(
				value === null
					? isMe
						? `Back to ${saved.name}`
						: `${saved.name} goes by their account name again`
					: isMe
						? 'Name updated'
						: `${member.name} is now ${saved.name} in this group`
			);
		} catch (err) {
			toasts.error(err);
		} finally {
			saving = false;
		}
	}
</script>

<Sheet
	bind:open
	title={isMe ? 'Your name in this group' : `${member.name}’s name in this group`}
	description={isMe
		? `Only ${groupName} sees this. Everywhere else you go by your account name.`
		: `Only ${groupName} sees this. Leave it blank to use their account name.`}
	width={440}
>
	<form id="member-name-form" onsubmit={(e) => save(toSave, e)}>
		<TextField
			label="Name in {groupName}"
			hideLabel
			bind:value={name}
			placeholder={isMe ? myAccountName : 'Their account name'}
			maxlength={80}
			autocomplete={isMe ? 'nickname' : 'off'}
		/>
	</form>
	{#snippet footer()}
		{#if member.nameInGroup !== null}
			<Button variant="secondary" disabled={saving} onclick={() => save(null)}>{accountLabel}</Button>
		{/if}
		<Button type="submit" form="member-name-form" variant="accent" loading={saving} disabled={!changed}>Save</Button>
	{/snippet}
</Sheet>
