<script lang="ts">
	import Button from './Button.svelte';
	import Sheet from './Sheet.svelte';

	interface Props {
		open: boolean;
		title: string;
		message: string;
		confirmLabel: string;
		cancelLabel?: string;
		/** Styles the confirm button as destructive: soft red, or solid red for the irreversible ones (Delete group). */
		destructive?: boolean | 'solid';
		/** `auto` slides up from the bottom on phones (a centred modal on wider screens). */
		placement?: 'auto' | 'center';
		onconfirm: () => Promise<unknown> | unknown;
	}
	let {
		open = $bindable(),
		title,
		message,
		confirmLabel,
		cancelLabel = 'Cancel',
		destructive = false,
		placement = 'auto',
		onconfirm
	}: Props = $props();

	let busy = $state(false);

	async function confirm() {
		busy = true;
		try {
			await onconfirm();
			open = false;
		} finally {
			busy = false;
		}
	}
</script>

<Sheet bind:open {title} description={message} {placement} width={420}>
	{#snippet children()}{/snippet}
	{#snippet footer()}
		<Button variant="secondary" onclick={() => (open = false)}>{cancelLabel}</Button>
		<Button variant={destructive === 'solid' ? 'destructive' : destructive ? 'danger' : 'solid'} loading={busy} onclick={confirm}>
			{confirmLabel}
		</Button>
	{/snippet}
</Sheet>
