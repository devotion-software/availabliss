<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { updateMe } from '#lib/client/me';
	import { toasts } from '#lib/client/toast.svelte';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';

	/**
	 * Whether plans show "Add to calendar" (on the plan, in the chat, Home's Up next and My plans).
	 * Off suits people who subscribe to their plans instead. Saved to the account, so every device follows.
	 */
	interface Props {
		show: boolean;
	}
	let { show }: Props = $props();

	let checked = $state((() => show)());

	async function save(next: boolean) {
		try {
			await updateMe({ showAddToCalendar: next });
			await invalidateAll();
			toasts.success(next ? 'Add to calendar buttons are back' : 'Add to calendar buttons hidden');
		} catch (err) {
			checked = !next;
			toasts.error(err);
		}
	}
</script>

<GlassPanel as="section" padding="16px" gap={8} aria-labelledby="add-to-calendar-title">
	<div class="row">
		<div class="text">
			<h2 class="eyebrow" id="add-to-calendar-title">Add to calendar buttons</h2>
			<p>On plans, in chats and on My plans. Turn them off if you subscribe to your plans instead.</p>
		</div>
		<Toggle bind:checked labelledby="add-to-calendar-title" onchange={save} />
	</div>
</GlassPanel>

<style>
	.row {
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	p {
		font-size: 13px;
		color: var(--muted);
	}
</style>
