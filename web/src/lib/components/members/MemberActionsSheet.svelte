<script lang="ts">
	import type { Member } from '#lib/types';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Icon, { type IconName } from '#lib/components/ui/Icon.svelte';

	/** What an admin can do to another member. */
	interface Props {
		open: boolean;
		member: Member;
		onrole: (role: 'admin' | 'member') => void;
		onremove: () => void;
	}
	let { open = $bindable(), member, onrole, onremove }: Props = $props();

	const actions = $derived<{ label: string; sub: string; icon: IconName; run: () => void; danger?: boolean }[]>([
		member.role === 'admin'
			? { label: 'Make a member', sub: 'They can no longer change timing or manage people', icon: 'user', run: () => onrole('member') }
			: { label: 'Make an admin', sub: 'They can change timing, invite and set replies', icon: 'shield', run: () => onrole('admin') },
		{ label: 'Remove from group', sub: 'Their busy times stop counting', icon: 'trash', run: onremove, danger: true }
	]);
</script>

<Sheet bind:open title={member.name} placement="center" width={400}>
	<div class="actions">
		{#each actions as a (a.label)}
			<button
				type="button"
				class:danger={a.danger}
				onclick={() => {
					open = false;
					a.run();
				}}
			>
				<Icon name={a.icon} size={18} />
				<span class="text">
					<span class="label">{a.label}</span>
					<span class="sub">{a.sub}</span>
				</span>
			</button>
		{/each}
	</div>
</Sheet>

<style>
	.actions {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	button {
		min-height: 56px;
		border-radius: 16px;
		border: 1px solid var(--glass-border);
		background: var(--surface);
		padding: 8px 14px;
		display: flex;
		align-items: center;
		gap: 12px;
		text-align: left;
	}
	.danger {
		color: var(--warn-ink);
	}
	.text {
		display: flex;
		flex-direction: column;
	}
	.label {
		font-size: 15px;
		font-weight: 600;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
	}
</style>
