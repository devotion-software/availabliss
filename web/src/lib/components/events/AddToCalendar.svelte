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
		size?: 'sm' | 'md' | 'lg';
		/** Stack the two buttons instead of placing them side by side. */
		stacked?: boolean;
		/** Put the Google Calendar link beside the button (for small cards, like the chat's "Everyone's in" banner). */
		inline?: boolean;
	}
	let { eventId, item, size = 'md', stacked = false, inline = false }: Props = $props();

	const fmt = useFormat();
	let added = $state(false);
	const google = $derived(googleCalendarUrl(item, fmt.current.dayKey));
</script>

<div class="add" class:stacked class:inline>
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
		<span>Google<span class="cal">&nbsp;Calendar</span></span>
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
	.inline {
		container-type: inline-size;
		flex-direction: row;
		align-items: center;
		gap: 4px;
	}
	.inline > :global(.btn) {
		flex: 0 1 auto;
		width: auto;
		min-width: 0;
	}
	.inline .google {
		flex: none;
		min-height: 38px;
		font-size: 13px;
	}
	/* Narrow cards (phones): "Google" alone, so the main button keeps its full label. */
	@container (max-width: 330px) {
		.inline .cal {
			display: none;
		}
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
