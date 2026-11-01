<script lang="ts">
	import Icon from '#lib/components/ui/Icon.svelte';

	/** "Connect another calendar" glass row. */
	interface Props {
		first?: boolean;
		onclick: () => void;
	}
	let { first = false, onclick }: Props = $props();
</script>

<button type="button" class="connect glass" {onclick}>
	<span class="plus"><Icon name="plus" size={16} strokeWidth={2.4} /></span>
	<span class="label">{first ? 'Connect a calendar' : 'Connect another calendar'}</span>
	<span class="hint">Google · Outlook · iCloud</span>
</button>

<style>
	.connect {
		min-height: 52px;
		border-radius: 20px;
		padding: 0 14px;
		display: flex;
		align-items: center;
		gap: 10px;
		font-size: 14px;
		font-weight: 600;
		text-align: left;
		width: 100%;
		transition: transform 0.15s ease;
	}
	.connect:hover {
		transform: translateY(-1px);
	}
	.plus {
		width: 30px;
		height: 30px;
		border-radius: 10px;
		background: var(--accent-soft);
		color: var(--accent-ink);
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.label {
		flex: 1;
	}
	.hint {
		font-size: 12px;
		font-weight: 500;
		color: var(--muted);
	}
</style>
