<script lang="ts">
	import { onMount } from 'svelte';
	import { showsAddToCalendar } from '#lib/client/add-to-calendar.svelte';
	import { eventIcsUrl } from '#lib/client/events';
	import { googleCalendarUrl, rememberAddedToCalendar, wasAddedToCalendar, type CalendarItem } from '#lib/client/calendar-links';
	import { useFormat } from '#lib/client/format';
	import Button from '#lib/components/ui/Button.svelte';
	import BrandIcon from '#lib/components/ui/BrandIcon.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';

	/**
	 * Web "Add to calendar": download the .ics, or open Google Calendar prefilled. "Added to your
	 * calendar" shows for a few seconds after; then (and on later visits, remembered in this
	 * browser) just a quiet "In your calendar" line, with Add again for the buttons.
	 */
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

	const CONFIRMATION_MS = 3000;
	const fmt = useFormat();
	/** Just added: "Added to your calendar" for a moment. */
	let added = $state(false);
	/** Added before (or a moment ago): the quiet line instead of the buttons. */
	let remembered = $state(false);
	let timer: ReturnType<typeof setTimeout> | undefined;
	const google = $derived(googleCalendarUrl(item, fmt.current.dayKey));

	onMount(() => {
		remembered = wasAddedToCalendar(eventId);
		return () => clearTimeout(timer);
	});

	function markAdded(confirm: boolean) {
		rememberAddedToCalendar(eventId);
		clearTimeout(timer);
		if (!confirm) {
			remembered = true;
			return;
		}
		added = true;
		timer = setTimeout(() => {
			added = false;
			remembered = true;
		}, CONFIRMATION_MS);
	}
</script>

{#if !showsAddToCalendar()}
	<!-- Turned off in Settings (e.g. subscribed to their plans instead). -->
{:else if remembered && !added}
	<p class="in-calendar">
		<span class="chip">
			<Icon name="check" size={13} strokeWidth={2.6} />
			In your calendar
		</span>
		<button type="button" class="again" onclick={() => (remembered = false)}>Add again</button>
	</p>
{:else}
	<div class="add" class:stacked class:inline>
		<Button
			href={eventIcsUrl(eventId)}
			download
			variant="solid"
			{size}
			icon={added ? 'check' : 'calendar-plus'}
			block
			class={added ? 'added' : ''}
			onclick={() => markAdded(true)}
		>
			{added ? 'Added to your calendar' : 'Add to calendar'}
		</Button>
		{#if !added}
			<a class="google" href={google} target="_blank" rel="noopener noreferrer" onclick={() => markAdded(false)}>
				<BrandIcon brand="google" size={16} />
				<span>Google<span class="cal">&nbsp;Calendar</span></span>
				<span class="sr-only">(opens in a new tab)</span>
			</a>
		{/if}
	</div>
{/if}

<style>
	/* A small chip that hugs its content, with Add again beside it. */
	.in-calendar {
		display: flex;
		align-items: center;
		gap: 4px;
		margin: 0;
	}
	.chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 28px;
		padding: 0 10px;
		border: 1px solid var(--line);
		border-radius: 999px;
		background: var(--surface);
		font-size: 13px;
		font-weight: 500;
		color: var(--ink);
		white-space: nowrap;
	}
	.chip :global(svg) {
		color: var(--ok-ink);
		flex: none;
	}
	.again {
		/* Tap area taller than it looks, without making the row taller. */
		margin: -8px 0;
		min-height: 44px;
		padding: 0 8px;
		font-size: 13px;
		font-weight: 600;
		color: var(--accent-ink);
	}
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
