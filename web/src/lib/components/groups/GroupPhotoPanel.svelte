<script lang="ts">
	import { invalidate } from '$app/navigation';
	import type { Group } from '#lib/types';
	import { removeGroupImage, setGroupImage } from '#lib/client/groups';
	import { takePhoto } from '#lib/client/photo';
	import { toasts } from '#lib/client/toast.svelte';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import Button from '#lib/components/ui/Button.svelte';

	/** Admins: add, change or remove the group's photo (shown instead of the monogram tile). */
	interface Props {
		group: Group;
	}
	let { group }: Props = $props();

	let input: HTMLInputElement | undefined = $state();
	let busy = $state(false);

	async function run(action: () => Promise<unknown>, done: string) {
		busy = true;
		try {
			await action();
			// The group's own details and the groups list (tiles in the sidebar and home) both show it.
			await Promise.all([invalidate('app:group'), invalidate('app:groups')]);
			toasts.success(done);
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}

	function pick(e: Event) {
		const file = takePhoto(e);
		if (file) run(() => setGroupImage(group.id, file), 'Group photo updated');
	}
</script>

<section class="photo glass" aria-labelledby="group-photo-title">
	<GroupTile monogram={group.monogram} color={group.color} image={group.image} size={56} />
	<div class="body">
		<h2 id="group-photo-title">Group photo</h2>
		<p class="lead">Everyone sees it in place of the {group.monogram} tile.</p>
		<div class="actions">
			<Button size="sm" variant="secondary" icon={group.image ? 'pencil' : 'plus'} loading={busy} onclick={() => input?.click()}>
				{group.image ? 'Change photo' : 'Add photo'}
			</Button>
			{#if group.image}
				<Button size="sm" variant="ghost" icon="trash" disabled={busy} onclick={() => run(() => removeGroupImage(group.id), 'Group photo removed')}>
					Remove
				</Button>
			{/if}
		</div>
		<input bind:this={input} class="sr-only" type="file" accept="image/*" tabindex="-1" aria-hidden="true" disabled={busy} onchange={pick} />
	</div>
</section>

<style>
	.photo {
		border-radius: 24px;
		padding: 16px;
		display: flex;
		align-items: flex-start;
		gap: 14px;
	}
	.body {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	h2 {
		font-size: 18px;
		font-weight: 650;
	}
	.lead {
		font-size: 13px;
		color: var(--muted);
	}
	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
		margin-top: 8px;
	}
</style>
