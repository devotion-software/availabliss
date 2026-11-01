<script lang="ts">
	import type { Member, Message } from '#lib/types';
	import AvatarStack from '#lib/components/ui/AvatarStack.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';

	/** Under your latest message: who's seen it (Chat.dc.html). Opens message info. */
	interface Props {
		message: Message;
		members: Map<string, Member>;
		onopen: () => void;
	}
	let { message, members, onopen }: Props = $props();

	const r = $derived(message.receipt);
	// Readers in the order they saw it (Message.receipt.seenBy).
	const seenIds = $derived(r?.seenBy ?? []);

	const people = $derived(
		seenIds.flatMap((id) => {
			const m = members.get(id);
			return m ? [{ id, name: m.name, image: m.image }] : [];
		})
	);
	const text = $derived.by(() => {
		if (!r || message.id.startsWith('temp-')) return 'Sending…';
		if (r.recipients === 0) return 'Sent';
		if (r.seen >= r.recipients) return 'Seen by everyone';
		if (r.seen > 0) return `Seen by ${r.seen} of ${r.recipients}`;
		if (r.delivered > 0) return `Delivered to ${r.delivered} of ${r.recipients}`;
		return 'Sent';
	});
</script>

<button type="button" class="receipt" onclick={onopen} aria-label="{text}. Show message info">
	{#if people.length}<AvatarStack {people} size={16} max={4} ringOn="bg" />{/if}
	{text}
	<span class="tick" class:seen={(r?.seen ?? 0) > 0}>
		<Icon name={(r?.delivered ?? 0) > 0 || (r?.seen ?? 0) > 0 ? 'double-check' : 'check'} size={16} strokeWidth={2.4} />
	</span>
</button>

<style>
	.receipt {
		align-self: flex-end;
		margin-top: -6px;
		min-height: 28px;
		padding: 0 2px;
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 11px;
		font-weight: 500;
		color: var(--muted);
	}
	.receipt:hover {
		color: var(--ink);
	}
	.tick {
		color: var(--muted);
	}
	.tick.seen {
		color: var(--accent-ink);
	}
</style>
