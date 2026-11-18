<script lang="ts">
	import { setMyName } from '#lib/client/groups';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';

	/**
	 * Straight after joining a group: how it should see you, prefilled with your account name.
	 * Skippable: keeping the account name, or closing the sheet, just goes on (`ondone`).
	 */
	interface Props {
		open: boolean;
		groupId: string;
		groupName: string;
		accountName: string;
		ondone: () => void;
	}
	let { open = $bindable(), groupId, groupName, accountName, ondone }: Props = $props();

	let name = $state('');
	let saving = $state(false);
	let finished = false;
	$effect.pre(() => {
		if (open) name = accountName;
	});

	function finish() {
		if (finished) return;
		finished = true;
		open = false;
		ondone();
	}

	async function useName(e?: Event) {
		e?.preventDefault();
		const trimmed = name.trim();
		if (!trimmed || saving) return;
		// The account name already: nothing to set.
		if (trimmed === accountName) return finish();
		saving = true;
		try {
			await setMyName(groupId, trimmed);
			finish();
		} catch (err) {
			toasts.error(err);
		} finally {
			saving = false;
		}
	}
</script>

<Sheet
	bind:open
	title="How should {groupName} see you?"
	description="You can go by a different name in this group, like a nickname. Only this group sees it, and you can change it any time in Group settings."
	placement="auto"
	width={440}
	onclose={finish}
>
	<form id="group-name-form" onsubmit={useName}>
		<TextField label="Your name in {groupName}" hideLabel bind:value={name} placeholder={accountName} maxlength={80} autocomplete="nickname" />
	</form>
	{#snippet footer()}
		<Button variant="secondary" disabled={saving} onclick={finish}>Keep {accountName}</Button>
		<Button type="submit" form="group-name-form" variant="accent" loading={saving} disabled={!name.trim()}>Use this name</Button>
	{/snippet}
</Sheet>
