<script lang="ts">
	import { eventIcsUrl } from '#lib/client/events';
	import { googleCalendarUrl, type CalendarItem } from '#lib/client/calendar-links';
	import { useFormat } from '#lib/client/format';
	import Button from '#lib/components/ui/Button.svelte';
	import BrandIcon from '#lib/components/ui/BrandIcon.svelte';

	/** Web "Add to calendar": download the .ics, or open Google Calendar prefilled. */
	interface Props {
		eventId: string;
		item: CalendarItem;
		size?: 'md' | 'lg';
		/** Stack the two buttons instead of placing them side by side. */
		stacked?: boolean;
	}
	let { eventId, item, size = 'md', stacked = false }: Props = $props();

	const fmt = useFormat();
	let added = $state(false);
	const google = $derived(googleCalendarUrl(item, fmt.current.dayKey));
</script>

<div class="add" class:stacked>
	<Button
		href={eventIcsUrl(eventId)}
		download
		variant="solid"
		{size}
		icon={added ? 'check' : 'calendar-plus'}
		block
		class={added ? 'added' : ''}
		onclick={() => (added = true)}
	>
		{added ? 'Added to your calendar' : 'Add to calendar'}
	</Button>
	<a class="google" href={google} target="_blank" rel="noopener noreferrer">
		<BrandIcon brand="google" size={16} />
		Google Calendar
		<span class="sr-only">(opens in a new tab)</span>
	</a>
</div>

<style>
	.add {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.add :global(.added) {
		background: var(--ok-bg);
		color: var(--ok-ink);
	}
	.google {
		align-self: center;
		display: inline-flex;
		align-items: center;
		gap: 8px;
		min-height: 44px;
		padding: 0 12px;
		border-radius: 12px;
		font-size: 14px;
		font-weight: 600;
		color: var(--ink);
		text-decoration: none;
	}
	.google:hover {
		background: var(--surface);
		color: var(--ink);
	}
	.stacked .google {
		align-self: stretch;
		justify-content: center;
		border: 1px solid var(--line-2);
		background: var(--surface);
		border-radius: 16px;
		min-height: 50px;
	}
</style>
