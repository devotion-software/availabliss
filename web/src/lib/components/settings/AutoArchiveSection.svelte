<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { updateMe } from '#lib/client/me';
	import { toasts } from '#lib/client/toast.svelte';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';

	/**
	 * Whether groups you create start with auto-archive on (archived once their latest plan has passed).
	 * Each group can still change it in its settings. Saved to the account, so every device follows.
	 */
	interface Props {
		on: boolean;
	}
	let { on }: Props = $props();

	let checked = $state((() => on)());

	async function save(next: boolean) {
		try {
			await updateMe({ autoArchiveNewGroups: next });
			await invalidateAll();
			toasts.success(next ? 'New groups you create will archive themselves' : 'New groups you create will stay put');
		} catch (err) {
			checked = !next;
			toasts.error(err);
		}
	}
</script>

<GlassPanel as="section" padding="16px" gap={8} aria-labelledby="auto-archive-title">
	<div class="row">
		<div class="text">
			<h2 class="eyebrow" id="auto-archive-title">Auto-archive groups I create</h2>
			<p>New groups you make archive themselves once their last plan has passed. You can change it for each group in its settings.</p>
		</div>
		<Toggle bind:checked labelledby="auto-archive-title" onchange={save} />
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
