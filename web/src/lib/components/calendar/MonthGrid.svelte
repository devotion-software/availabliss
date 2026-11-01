<script lang="ts">
	import type { Availability, AvailabilityDay, Member } from '#lib/types';
	import { dayNumber, formatDayKey, useFormat, weekdayName } from '#lib/client/format';
	import { WEEKDAY_NAMES, weekdayShort } from '#lib/client/requirements';
	import { busyNames, dayMap, monthRows, slotOn, visibleColumns } from './grid';
	import CalendarLegend from './CalendarLegend.svelte';

	/**
	 * Month of group availability. Only the weekday columns the timing can use
	 * are drawn; days that work for everyone are accent, multi-day slots join
	 * into one band, busy days are faded.
	 */
	interface Props {
		availability: Availability;
		/** Any date in the month to show. */
		month: string;
		members: Map<string, Member>;
		selected?: string | null;
		onselect?: (date: string) => void;
		/** Phone density (54px cells) vs desktop (76px). */
		compact?: boolean;
		legend?: boolean;
	}
	let { availability, month, members, selected = null, onselect, compact = false, legend = true }: Props = $props();

	const fmt = useFormat();
	const columns = $derived(visibleColumns(availability));
	const rows = $derived(monthRows(month, columns));
	const days = $derived(dayMap(availability.days));
	const today = $derived(fmt.current.today());

	let grid: HTMLElement | undefined = $state();

	/** The small line under the date number. */
	function note(date: string, day: AvailabilityDay | undefined): string {
		if (!day) return '';
		if (day.status === 'match') {
			const slot = slotOn(availability.slots, date, fmt.current.dayKey);
			const starts = slot && fmt.current.dayKey(slot.start) === date ? fmt.current.time(slot.start) : null;
			if (starts && starts !== '00:00') return compact ? `from ${starts}` : `Free from ${starts}`;
			return compact ? 'all free' : 'Everyone free';
		}
		if (day.status === 'busy' && day.busyMemberIds.length) return `${busyNames(day.busyMemberIds, members, true)} busy`;
		if (day.holiday) return day.holiday;
		return date === today ? 'Today' : '';
	}

	function label(date: string, day: AvailabilityDay | undefined): string {
		const parts = [`${weekdayName(date, 'long')} ${formatDayKey(date).split(' ').slice(1).join(' ')}`];
		if (date === today) parts.push('today');
		if (day?.status === 'match') parts.push('works for everyone');
		else if (day?.status === 'busy') parts.push(day.busyMemberIds.length ? `${busyNames(day.busyMemberIds, members)} busy` : 'someone is busy');
		else if (day?.status === 'free') parts.push('free, but not for the whole timing');
		if (day?.holiday) parts.push(day.holiday);
		return parts.join(', ');
	}

	// Arrow keys move between day cells like a grid.
	function onkeydown(e: KeyboardEvent) {
		const moves: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -columns.length, ArrowDown: columns.length };
		const step = moves[e.key];
		if (!step || !grid) return;
		const cells = [...grid.querySelectorAll<HTMLElement>('[data-cell]')];
		const index = cells.indexOf(document.activeElement as HTMLElement);
		if (index < 0) return;
		e.preventDefault();
		let next = index + step;
		while (next >= 0 && next < cells.length && cells[next].hasAttribute('disabled')) next += Math.sign(step);
		cells[next]?.focus();
	}
</script>

<div class="month" class:compact style:--cols={columns.length}>
	<div class="heads" aria-hidden="true">
		{#each columns as c (c)}
			<span>{compact ? weekdayShort(c) : WEEKDAY_NAMES[c]}</span>
		{/each}
	</div>
	<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
	<div class="cells" bind:this={grid} role="group" aria-label="Days" {onkeydown}>
		{#each rows as row, r (r)}
			{#each row as date, c (c)}
				{@const day = date ? days.get(date) : undefined}
				{#if !date || !day}
					<span class="cell empty" aria-hidden="true"></span>
				{:else if day.status === 'hidden'}
					<!-- Days that can't be part of any slot (e.g. already past) stay as quiet placeholders. -->
					<span class="cell hidden-day" class:today={date === today} aria-hidden="true">
						<span class="num">{dayNumber(date)}</span>
						<span class="note">{date === today ? 'Today' : ''}</span>
					</span>
				{:else}
					<button
						type="button"
						data-cell
						class="cell {day.status} {day.slotPosition ?? ''}"
						class:selected={selected === date}
						class:today={date === today}
						aria-pressed={selected === date}
						aria-current={date === today ? 'date' : undefined}
						aria-label={label(date, day)}
						onclick={() => onselect?.(date)}
					>
						<span class="num">{dayNumber(date)}</span>
						<span class="note">{note(date, day)}</span>
					</button>
				{/if}
			{/each}
		{/each}
	</div>
	{#if legend}<CalendarLegend {columns} {compact} />{/if}
</div>

<style>
	.month {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.compact {
		gap: 6px;
	}
	.heads,
	.cells {
		display: grid;
		grid-template-columns: repeat(var(--cols), minmax(0, 1fr));
		gap: 6px;
	}
	.compact .heads,
	.compact .cells {
		gap: 4px;
	}
	.heads span {
		padding-left: 6px;
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.compact .heads span {
		padding-left: 10px;
		font-size: 11px;
	}

	.cell {
		min-height: 76px;
		min-width: 0;
		padding: 10px;
		border-radius: 16px;
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		justify-content: space-between;
		text-align: left;
		color: var(--ink);
		border: 1px solid transparent;
		transition:
			transform 0.15s ease,
			box-shadow 0.2s ease;
	}
	.compact .cell {
		min-height: 54px;
		height: 54px;
		padding: 0 10px;
		justify-content: center;
		gap: 4px;
	}
	button.cell:hover {
		transform: translateY(-2px);
	}
	.compact button.cell:hover {
		transform: scale(1.03);
	}
	.empty {
		visibility: hidden;
	}
	.hidden-day {
		border: 1px dashed var(--line-2);
		color: var(--muted);
	}
	.hidden-day .num {
		opacity: 0.7;
	}
	.num {
		font-size: 15px;
		font-weight: 650;
		line-height: 1;
	}
	.compact .num {
		font-size: 17px;
	}
	.note {
		max-width: 100%;
		font-size: 11px;
		font-weight: 600;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		min-height: 1.2em;
	}
	.compact .note:empty {
		display: none;
	}

	.free {
		background: var(--surface);
		border-color: var(--glass-border);
	}
	.busy {
		background: var(--busy-bg);
		border-color: var(--glass-border);
		opacity: 0.5;
	}
	.match {
		background: var(--accent);
		color: var(--on-accent);
		box-shadow: 0 14px 28px -16px var(--accent);
	}
	/* Multi-day slots read as one band. */
	.match.start {
		border-radius: 18px 6px 6px 18px;
	}
	.match.middle {
		border-radius: 6px;
	}
	.match.end {
		border-radius: 6px 18px 18px 6px;
	}
	.compact .match.start {
		border-radius: 16px 5px 5px 16px;
	}
	.compact .match.middle {
		border-radius: 5px;
	}
	.compact .match.end {
		border-radius: 5px 16px 16px 5px;
	}
	.today:not(.match) .num {
		color: var(--accent-ink);
	}
	.selected {
		outline: 2px solid var(--ink);
		outline-offset: 2px;
	}
	.busy.selected {
		opacity: 0.75;
	}
</style>
