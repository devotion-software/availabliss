<script lang="ts">
	import { formatDayKey } from '#lib/client/format';
	import Icon from './Icon.svelte';
	import Toggle from './Toggle.svelte';
	import MonthCalendar from './MonthCalendar.svelte';
	import TimeWheel from './TimeWheel.svelte';
	import { spokenDate, withAllDay, withEnd, withStart, type RangeFields } from './date-range';

	/**
	 * The one date range input (blocks, events, proposals): an "All day" switch, then the start
	 * (date over time) → the end (date over time). Each date and time is a chip that opens its
	 * picker under the row. Moving the start keeps the duration; the end can't go before the start.
	 * All day hides the times; the end date is the last day (inclusive).
	 */
	interface Props {
		startDate: string;
		startTime: string;
		endDate: string;
		endTime: string;
		allDay: boolean;
		/** Marked on the calendars. */
		today?: string | null;
		/** Earliest / latest dates the range can use. */
		minDate?: string | null;
		maxDate?: string | null;
	}
	let {
		startDate = $bindable(),
		startTime = $bindable(),
		endDate = $bindable(),
		endTime = $bindable(),
		allDay = $bindable(),
		today = null,
		minDate = null,
		maxDate = null
	}: Props = $props();

	const uid = $props.id();
	type Picker = 'startDate' | 'startTime' | 'endDate' | 'endTime';
	let open = $state<Picker | null>(null);

	const fields = (): RangeFields => ({ startDate, startTime, endDate, endTime, allDay });
	function apply(r: RangeFields) {
		startDate = r.startDate;
		startTime = r.startTime;
		endDate = r.endDate;
		endTime = r.endTime;
		allDay = r.allDay;
	}
	const toggle = (p: Picker) => (open = open === p ? null : p);

	function pick(p: Picker, v: string) {
		if (p === 'startDate') apply(withStart(fields(), { date: v }));
		else if (p === 'startTime') apply(withStart(fields(), { time: v }));
		else if (p === 'endDate') apply(withEnd(fields(), { date: v }));
		else apply(withEnd(fields(), { time: v }));
		// A date is one tap; times stay open so the minutes can follow the hour.
		if (p === 'startDate' || p === 'endDate') open = null;
	}

	function onAllDay(on: boolean) {
		apply(withAllDay(fields(), on));
		if (on && (open === 'startTime' || open === 'endTime')) open = null;
	}

	const pickerLabel: Record<Picker, string> = { startDate: 'Start date', startTime: 'Start time', endDate: 'End date', endTime: 'End time' };
	const dateText = (d: string) => (d ? formatDayKey(d) : 'Choose');
</script>

{#snippet side(which: 'start' | 'end')}
	{@const date = which === 'start' ? startDate : endDate}
	{@const time = which === 'start' ? startTime : endTime}
	{@const dateKey = (which === 'start' ? 'startDate' : 'endDate') as Picker}
	{@const timeKey = (which === 'start' ? 'startTime' : 'endTime') as Picker}
	<div class="side {which}">
		<button
			type="button"
			class="chip date"
			class:open={open === dateKey}
			aria-expanded={open === dateKey}
			aria-controls="picker-{uid}"
			aria-label="{which === 'start' ? 'Starts' : 'Ends'}, {date ? spokenDate(date) : 'no date'}"
			onclick={() => toggle(dateKey)}
		>
			{dateText(date)}
		</button>
		{#if !allDay}
			<button
				type="button"
				class="chip time"
				class:open={open === timeKey}
				aria-expanded={open === timeKey}
				aria-controls="picker-{uid}"
				aria-label="{which === 'start' ? 'Start' : 'End'} time, {time || 'not set'}"
				onclick={() => toggle(timeKey)}
			>
				{time || '--:--'}
			</button>
		{/if}
	</div>
{/snippet}

<div class="range glass">
	<div class="top">
		<span id="allday-{uid}">All day</span>
		<Toggle checked={allDay} labelledby="allday-{uid}" onchange={onAllDay} />
	</div>
	<div class="row" class:dates-only={allDay}>
		{@render side('start')}
		<span class="arrow" aria-hidden="true"><Icon name="arrow-right" size={18} /></span>
		{@render side('end')}
	</div>
	{#if open}
		<div class="picker" id="picker-{uid}">
			{#if open === 'startDate'}
				<MonthCalendar value={startDate} min={minDate} max={maxDate} {today} label={pickerLabel.startDate} onpick={(d) => pick('startDate', d)} />
			{:else if open === 'endDate'}
				<MonthCalendar
					value={endDate}
					min={startDate || minDate}
					max={maxDate}
					{today}
					label={pickerLabel.endDate}
					onpick={(d) => pick('endDate', d)}
				/>
			{:else if open === 'startTime'}
				<TimeWheel value={startTime} label={pickerLabel.startTime} onpick={(t) => pick('startTime', t)} />
			{:else}
				<TimeWheel value={endTime} label={pickerLabel.endTime} onpick={(t) => pick('endTime', t)} />
			{/if}
		</div>
	{/if}
</div>

<style>
	.range {
		border-radius: 22px;
		padding: 6px 10px 10px;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		min-height: 48px;
		padding: 0 2px 0 6px;
		font-size: 15px;
		font-weight: 600;
		border-bottom: 1px solid var(--line);
	}
	.row {
		display: grid;
		grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
		align-items: center;
		gap: 4px;
		padding-top: 6px;
	}
	.side {
		display: flex;
		flex-direction: column;
		align-items: center;
		min-width: 0;
	}
	.arrow {
		color: var(--muted);
		display: grid;
		place-items: center;
	}
	.chip {
		min-height: 44px;
		max-width: 100%;
		padding: 0 10px;
		border-radius: var(--radius-pill);
		color: var(--ink);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		transition: color 0.15s ease;
	}
	.chip.date {
		font-size: 15px;
		font-weight: 600;
	}
	.chip.time {
		min-height: 46px;
		font-family: var(--font-display);
		font-size: 28px;
		font-weight: 700;
		letter-spacing: -0.01em;
		font-variant-numeric: tabular-nums;
	}
	.chip.open {
		color: var(--accent-ink);
	}
	.chip:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: 1px;
	}
	.picker {
		border-top: 1px solid var(--line);
		padding-top: 8px;
		margin-top: 2px;
	}
	/* Very narrow: stack start over end, the arrow pointing down. */
	@media (max-width: 330px) {
		.row {
			grid-template-columns: minmax(0, 1fr);
		}
		.arrow {
			transform: rotate(90deg);
		}
	}
</style>
