<script lang="ts">
	import type { BusyInterval } from '#lib/types';
	import { dayNumber, weekdayName } from '#lib/client/format';
	import type { PlacedInterval } from './parts';
	import { busyRanges, counts, dayNames, describeDay, freeSummary, STRIP_FROM, STRIP_TO } from './days';

	/**
	 * One day in the My time list: the date, a thin 06:00 – 24:00 strip of busy
	 * time (calendar busy faded, your own blocks striped) and a one-line summary.
	 * The whole row opens the day.
	 */
	interface Props {
		day: string;
		today: string;
		timed: PlacedInterval[];
		allDay: BusyInterval[];
		/** The day showing in the side panel (wide screens). */
		selected?: boolean;
		onclick: () => void;
	}
	let { day, today, timed, allDay, selected = false, onclick }: Props = $props();

	const busy = $derived(busyRanges(timed, allDay));
	const summary = $derived(freeSummary(busy));
	const names = $derived(dayNames(timed, allDay));
	const label = $derived(describeDay(day, busy, names));

	const SPAN = STRIP_TO - STRIP_FROM;
	/** Pieces drawn on the strip: whole days first, then timed busy within 06:00 – 24:00. */
	const marks = $derived([
		...allDay.filter(counts).map((i) => ({ source: i.source, left: 0, width: 100 })),
		...timed
			.filter((p) => counts(p.interval) && p.to > STRIP_FROM)
			.map((p) => {
				const from = Math.max(p.from, STRIP_FROM);
				return { source: p.interval.source, left: ((from - STRIP_FROM) / SPAN) * 100, width: ((p.to - from) / SPAN) * 100 };
			})
	]);
	// Calendar busy underneath, your own blocks on top.
	const ordered = $derived([...marks].sort((a, b) => (a.source === b.source ? 0 : a.source === 'calendar' ? -1 : 1)));
</script>

<button type="button" class="row" class:today={day === today} class:selected aria-label={label} aria-current={selected ? 'date' : undefined} {onclick}>
	<span class="date" aria-hidden="true">
		<span class="wd">{weekdayName(day)}</span>
		<span class="num">{dayNumber(day)}</span>
	</span>
	<span class="main" aria-hidden="true">
		<span class="line">
			<span class="summary" class:busy={summary === 'Busy all day'}>{summary}</span>
			{#if names.length}<span class="names">· {names.join(', ')}</span>{/if}
		</span>
		<span class="strip">
			<i class="tick" style="left: 33.333%"></i>
			<i class="tick" style="left: 66.667%"></i>
			{#each ordered as m, i (i)}
				<span class="mark {m.source}" style="left: {m.left}%; width: max(3px, {m.width}%)"></span>
			{/each}
		</span>
	</span>
</button>

<style>
	.row {
		width: 100%;
		min-height: 60px;
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 8px 14px 8px 8px;
		border-radius: 18px;
		text-align: left;
		color: var(--ink);
		transition: background 0.15s ease;
	}
	.row:hover {
		background: color-mix(in srgb, var(--surface) 70%, transparent);
	}
	.row:active {
		background: var(--surface);
	}
	.row.selected {
		background: var(--surface);
		box-shadow: inset 0 0 0 1.5px var(--accent);
	}
	.date {
		flex: none;
		width: 44px;
		display: flex;
		flex-direction: column;
		align-items: center;
		line-height: 1.1;
	}
	.wd {
		font-size: 11px;
		font-weight: 600;
		color: var(--muted);
	}
	.num {
		font-family: var(--font-display);
		font-size: 18px;
		font-weight: 650;
		width: 32px;
		height: 32px;
		display: grid;
		place-items: center;
		border-radius: 999px;
		font-variant-numeric: tabular-nums;
	}
	.today .wd {
		color: var(--accent-ink);
	}
	.today .num {
		background: var(--accent);
		color: var(--on-accent);
	}
	.main {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 7px;
	}
	.line {
		display: flex;
		gap: 6px;
		min-width: 0;
		font-size: 14px;
		white-space: nowrap;
	}
	.summary {
		flex: none;
		font-weight: 600;
	}
	.summary.busy {
		color: var(--muted);
	}
	.names {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		color: var(--muted);
	}
	.strip {
		position: relative;
		height: 8px;
		border-radius: 999px;
		background: color-mix(in srgb, var(--surface) 70%, transparent);
		box-shadow: inset 0 0 0 1px var(--line);
		overflow: hidden;
	}
	.tick {
		position: absolute;
		top: 0;
		bottom: 0;
		width: 1px;
		background: var(--line-3, var(--line));
		opacity: 0.8;
	}
	.mark {
		position: absolute;
		top: 0;
		bottom: 0;
		border-radius: 999px;
	}
	.mark.calendar {
		background: var(--busy-line);
	}
	.mark.manual {
		background: repeating-linear-gradient(135deg, var(--accent) 0 3px, color-mix(in srgb, var(--accent) 55%, transparent) 3px 6px);
	}
</style>
