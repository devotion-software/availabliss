<script lang="ts">
	import type { UpcomingEvent } from '#lib/client/events';
	import { useFormat } from '#lib/client/format';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import AddToCalendar from '#lib/components/events/AddToCalendar.svelte';

	/** The next confirmed plan across all groups (Main.dc "Up next"). */
	interface Props {
		event: UpcomingEvent;
	}
	let { event }: Props = $props();
	const fmt = useFormat();
</script>

<section class="next glass" aria-label="Up next">
	<div class="top">
		<StatusPill tone="mint" icon="check" size="md">Everyone's in</StatusPill>
		<a class="group" href="/app/groups/{event.groupId}">{event.groupName}</a>
	</div>
	<div class="what">
		<h2>{event.allDay ? fmt.current.when(event.start, event.end, true) : fmt.current.dateTime(event.start)}</h2>
		<span class="sub">{[event.title, event.location && `At ${event.location}`].filter(Boolean).join(' · ')}</span>
	</div>
	<AddToCalendar eventId={event.id} item={event} />
</section>

<style>
	.next {
		border-radius: 28px;
		padding: 18px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
	}
	.group {
		font-size: 12px;
		font-weight: 500;
		color: var(--muted);
		text-decoration: none;
	}
	.group:hover {
		color: var(--ink);
	}
	.what {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	h2 {
		font-size: 22px;
		font-weight: 650;
		line-height: 1.15;
	}
	.sub {
		font-size: 14px;
		color: var(--muted);
	}
</style>
