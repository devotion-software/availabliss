<script lang="ts">
	import Button from './Button.svelte';
	import Sheet from './Sheet.svelte';

	interface Props {
		open: boolean;
		title: string;
		message: string;
		confirmLabel: string;
		cancelLabel?: string;
		/** Styles the confirm button as destructive. */
		destructive?: boolean;
		onconfirm: () => Promise<unknown> | unknown;
	}
	let { open = $bindable(), title, message, confirmLabel, cancelLabel = 'Cancel', destructive = false, onconfirm }: Props =
		$props();

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

<Sheet bind:open {title} description={message} placement="center" width={420}>
	{#snippet children()}{/snippet}
	{#snippet footer()}
		<Button variant="secondary" onclick={() => (open = false)}>{cancelLabel}</Button>
		<Button variant={destructive ? 'danger' : 'solid'} loading={busy} onclick={confirm}>{confirmLabel}</Button>
	{/snippet}
</Sheet>
