<script lang="ts">
	import type { ResponseKind } from '#lib/types';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';

	/** Admin override: set someone's reply on their behalf. */
	interface Props {
		open: boolean;
		name: string;
		current: ResponseKind;
		onpick: (response: ResponseKind) => void;
	}
	let { open = $bindable(), name, current, onpick }: Props = $props();

	const OPTIONS: { value: ResponseKind; label: string; sub: string }[] = [
		{ value: 'accepted', label: 'Accepted', sub: 'They can make it' },
		{ value: 'declined', label: "Can't make it", sub: 'They told you they can’t' },
		{ value: 'pending', label: 'Waiting', sub: 'Clear their reply' }
	];
</script>

<Sheet bind:open title="Set {name}'s reply" description="Only admins can do this. They'll see it was set by you." placement="center" width={400}>
	<div class="options" role="group" aria-label="Reply">
		{#each OPTIONS as o (o.value)}
			<button
				type="button"
				class:on={o.value === current}
				aria-pressed={o.value === current}
				onclick={() => {
					open = false;
					if (o.value !== current) onpick(o.value);
				}}
			>
				<span class="text">
					<span class="label">{o.label}</span>
					<span class="sub">{o.sub}</span>
				</span>
				{#if o.value === current}<Icon name="check" size={18} strokeWidth={2.6} />{/if}
			</button>
		{/each}
	</div>
</Sheet>

<style>
	.options {
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
	button.on {
		border-color: var(--accent);
		color: var(--accent-ink);
	}
	.text {
		flex: 1;
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
