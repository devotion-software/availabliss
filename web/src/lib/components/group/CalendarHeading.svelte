<script lang="ts">
	import type { Group } from '#lib/types';
	import { formatDayKey } from '#lib/client/format';
	import Icon from '#lib/components/ui/Icon.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import SyncBanner from './SyncBanner.svelte';
	import { CALENDAR_VIEWS, type CalendarView } from './views';

	/**
	 * A group calendar's heading and navigation: the month or week shown, previous / next, "Today",
	 * the Month / Week switch on phones, and the jumps to the nearest free dates either side.
	 */
	interface Props {
		view: CalendarView;
		/** "November 2026", "27 – 29 November". */
		heading: string;
		/** For phones: "Nov 2026", "27–29 Nov". */
		shortHeading: string;
		loadingMonth: boolean;
		/** The desktop layout is (or may be) showing. */
		desk: boolean;
		group: Pick<Group, 'id' | 'members'>;
		isAdmin: boolean;
		/** The month or week on screen doesn't include today. */
		awayFromToday: boolean;
		/** The nearest free dates before / after what's on screen (null when there's none that way). */
		previousFree: string | null;
		nextFree: string | null;
		/** Still looking for those. */
		findingFree: boolean;
		onstep: (direction: 1 | -1) => void;
		ontoday: () => void;
		onjump: (date: string) => void;
	}
	let {
		view = $bindable(),
		heading,
		shortHeading,
		loadingMonth,
		desk,
		group,
		isAdmin,
		awayFromToday,
		previousFree,
		nextFree,
		findingFree,
		onstep,
		ontoday,
		onjump
	}: Props = $props();
</script>

<div class="cal-head">
	<h2 id="cal-heading"><span class="desk-inline">{heading}</span><span class="phone-inline">{shortHeading}</span></h2>
	{#if loadingMonth}<Spinner size={16} label="Loading month" />{/if}
	{#if desk}<span class="desk-only"><SyncBanner groupId={group.id} members={group.members} canNudge={isAdmin} variant="pill" /></span>{/if}
	<span class="nav desk-flex">
		<IconButton icon="chevron-left" label="Previous {view}" variant="surface" shape="rounded" size={40} onclick={() => onstep(-1)} />
		<IconButton icon="chevron-right" label="Next {view}" variant="surface" shape="rounded" size={40} onclick={() => onstep(1)} />
		{#if awayFromToday}<button type="button" class="today-btn" onclick={ontoday}>Today</button>{/if}
	</span>
	<span class="nav phone-flex">
		<IconButton icon="chevron-left" label="Previous {view}" variant="plain" shape="rounded" size={36} onclick={() => onstep(-1)} />
		<IconButton icon="chevron-right" label="Next {view}" variant="plain" shape="rounded" size={36} onclick={() => onstep(1)} />
		{#if awayFromToday}<button type="button" class="today-btn" onclick={ontoday}>Today</button>{/if}
	</span>
	<span class="phone-only">
		<SegmentedControl options={CALENDAR_VIEWS} bind:value={view} label="Calendar view" size="sm" />
	</span>
</div>

<!-- "‹ Fri 25 Dec · Free dates · Fri 5 Feb ›": skips months or weeks where nothing works. -->
<div class="free-jumps">
	<button
		type="button"
		class="free-jump"
		disabled={!previousFree}
		onclick={() => previousFree && onjump(previousFree)}
		aria-label={previousFree
			? `Previous free date, ${formatDayKey(previousFree)}`
			: findingFree
				? 'Previous free date, looking'
				: 'No earlier free dates'}
	>
		<Icon name="chevron-left" size={13} />
		{previousFree ? formatDayKey(previousFree) : findingFree ? 'Earlier' : 'None earlier'}
	</button>
	<span class="free-label" aria-hidden="true">Free dates</span>
	<button
		type="button"
		class="free-jump"
		disabled={!nextFree}
		onclick={() => nextFree && onjump(nextFree)}
		aria-label={nextFree ? `Next free date, ${formatDayKey(nextFree)}` : findingFree ? 'Next free date, looking' : 'No later free dates'}
	>
		{nextFree ? formatDayKey(nextFree) : findingFree ? 'Later' : 'None later'}
		<Icon name="chevron-right" size={13} />
	</button>
</div>

<style>
	/* On the narrowest phones the Month / Week switch wraps under the heading. */
	.cal-head {
		display: flex;
		align-items: center;
		gap: 6px 8px;
		flex-wrap: wrap;
	}
	h2 {
		flex: 0 1 auto;
		font-size: 19px;
		font-weight: 650;
		white-space: nowrap;
	}
	.cal-head > .phone-only:last-child {
		margin-left: auto;
		flex: none;
	}
	.desk-inline,
	.nav.desk-flex {
		display: none;
	}
	.nav {
		display: flex;
		gap: 6px;
	}
	.nav.phone-flex {
		display: flex;
		gap: 0;
		margin-left: -4px;
	}
	.desk-only {
		display: none;
	}
	.phone-only {
		display: block;
	}

	@media (min-width: 900px) {
		.phone-only {
			display: none;
		}
		.desk-only {
			display: block;
		}
		.desk-inline {
			display: inline;
		}
		.phone-inline,
		.nav.phone-flex {
			display: none;
		}
		.nav.desk-flex {
			display: flex;
		}
		h2 {
			flex: 1;
			font-size: 20px;
		}
	}
	/* "Today": back to today's month or week. */
	.today-btn {
		align-self: center;
		padding: 5px 11px;
		border: 0;
		border-radius: 999px;
		background: var(--accent-soft);
		color: var(--accent-ink);
		font: inherit;
		font-size: 13px;
		font-weight: 650;
		cursor: pointer;
	}
	.today-btn:hover {
		filter: brightness(0.97);
	}
	/* "‹ Fri 25 Dec · Free dates · Fri 5 Feb ›": greyed out when there's none that way. */
	.free-jumps {
		display: grid;
		grid-template-columns: 1fr auto 1fr;
		align-items: center;
		gap: 8px;
	}
	.free-jump {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		min-height: 30px;
		padding: 4px 10px;
		border: 0;
		border-radius: 999px;
		background: var(--accent-soft);
		color: var(--accent-ink);
		font: inherit;
		font-size: 13px;
		font-weight: 650;
		white-space: nowrap;
		cursor: pointer;
	}
	.free-jump:first-child {
		justify-self: start;
	}
	.free-jump:last-child {
		justify-self: end;
	}
	.free-jump:hover:not(:disabled) {
		filter: brightness(0.97);
	}
	.free-jump:disabled {
		background: var(--line);
		color: var(--muted);
		opacity: 0.7;
		cursor: default;
	}
	.free-label {
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
	}
</style>
