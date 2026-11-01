<script lang="ts">
	import type { Message } from '#lib/types';
	import type { GroupEvent } from '#lib/types';
	import { eventIcsUrl, getEventCached } from '#lib/client/events';
	import { useFormat } from '#lib/client/format';
	import Icon from '#lib/components/ui/Icon.svelte';

	/** An event added to the group, posted in the chat. */
	interface Props {
		message: Message;
		compact?: boolean;
	}
	let { message, compact = false }: Props = $props();

	const fmt = useFormat();
	let event = $state<GroupEvent | null>(null);
	$effect(() => {
		const id = message.eventId;
		event = null;
		if (!id) return;
		let alive = true;
		getEventCached(id)
			.then((e) => alive && (event = e))
			.catch(() => {});
		return () => {
			alive = false;
		};
	});
	const when = $derived(event ? fmt.current.when(event.start, event.end, event.allDay) : null);
</script>

<div class="card glass" class:compact>
	<span class="icon"><Icon name="calendar" size={20} /></span>
	<span class="text">
		<span class="title">{event?.title ?? message.body ?? 'An event was added'}</span>
		{#if when}<span class="meta">{[when, event?.location].filter(Boolean).join(' · ')}</span>{/if}
	</span>
	{#if message.eventId}
		<a href={eventIcsUrl(message.eventId)} download aria-label="Add to calendar">
			<Icon name="calendar-plus" size={18} />
		</a>
	{/if}
</div>

<style>
	.card {
		align-self: stretch;
		border-radius: 20px;
		padding: 10px 10px 10px 12px;
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.compact {
		background: var(--surface);
		box-shadow: none;
		backdrop-filter: none;
		-webkit-backdrop-filter: none;
		border-radius: 16px;
	}
	.icon {
		width: 38px;
		height: 38px;
		flex: none;
		border-radius: 12px;
		background: var(--ok-bg);
		color: var(--ok-ink);
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.title {
		font-size: 14px;
		font-weight: 600;
	}
	.meta {
		font-size: 12px;
		color: var(--muted);
	}
	a {
		width: 44px;
		height: 44px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--accent-ink);
	}
	a:hover {
		background: var(--accent-soft);
	}
</style>
