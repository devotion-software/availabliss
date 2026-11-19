<script lang="ts">
	import type { DayAvailability, DayStatus, Member } from '#lib/types';
	import { formatDayKey, useFormat } from '#lib/client/format';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { busyNames, missingNote } from './grid';
	import { axisFor, clampToAxis, hourTicks, mergeAxes, pct, spanText, subtractSpans, type Axis } from './timeline';

	/**
	 * A week of the group's visible days: shared free time as accent blocks, time only most of the
	 * group can make (with a quorum or optional members) as soft accent blocks, everything else as
	 * faded busy blocks.
	 */
	interface Props {
		dates: readonly string[];
		/** Day detail per date; missing = still loading, null = outside the timing. */
		days: ReadonlyMap<string, DayAvailability | null>;
		members: Map<string, Member>;
		onselect?: (date: string) => void;
		/** The day whose detail shows under the grid. */
		selected?: string | null;
		/** Phones in portrait: a card per day instead of the time grid, which needs the width. */
		cards?: boolean;
		/** A bank holiday's name on that date (shown as a flag by the date), or null. */
		bankHoliday?: (date: string) => string | null;
		/** The day's status from the month (undefined while unknown): its date is drawn like its month cell. */
		status?: (date: string) => { status: DayStatus; partial: boolean } | undefined;
	}
	let { dates, days, members, onselect, selected, cards = false, bankHoliday = () => null, status = () => undefined }: Props = $props();

	/**
	 * How a day's date looks, as in the month: accent when it works (soft when only most of the
	 * group can), dark when someone's busy, plain when free, and dashed for a day that's past or
	 * outside the timing. Unknown days look free.
	 */
	function look(d: string): 'match' | 'partial' | 'busy' | 'free' | 'out' {
		const s = status(d);
		if (d < today || s?.status === 'hidden') return 'out';
		if (!s) return 'free';
		if (s.status === 'match') return s.partial ? 'partial' : 'match';
		return s.status;
	}
	/** How far the day steps back, as in the month: busy days half, past and outside days more. */
	const fade = (d: string) => (look(d) === 'out' ? 'far' : look(d) === 'busy' ? 'dim' : '');

	const fmt = useFormat();
	const today = $derived(fmt.current.today());
	const tz = $derived(fmt.current.timeZone);

	const axis: Axis = $derived(
		mergeAxes(
			dates.flatMap((d) => {
				const day = days.get(d);
				return day
					? [
							axisFor(
								d,
								day.members.flatMap((m) => m.busy),
								tz
							)
						]
					: [];
			})
		)
	);
	const ticks = $derived(hourTicks(axis));

	interface Block {
		from: number;
		to: number;
		free: boolean;
		/** Free for enough of the group, not everyone. */
		most?: boolean;
		label: string;
	}

	function blocks(date: string, day: DayAvailability): Block[] {
		const range = day.window ? clampToAxis(day.window, date, axis, tz) : { from: axis.start, to: axis.end };
		if (!range) return [];
		const clamp = (list: DayAvailability['everyoneFree']) =>
			list
				.map((i) => clampToAxis(i, date, axis, tz))
				.filter((x) => x !== null)
				.sort((a, b) => a.from - b.from);
		const free = clamp(day.everyoneFree);
		// `mostFree` includes everyone's free time: keep only the stretches where someone's missing.
		const most = subtractSpans(clamp(day.mostFree ?? []), free);
		const out: Block[] = [];
		let cursor = range.from;
		const busyIds = (from: number, to: number) =>
			day.members
				.filter((m) =>
					m.busy.some((b) => {
						const s = clampToAxis(b, date, axis, tz);
						return s && s.from < to && s.to > from;
					})
				)
				.map((m) => m.userId);
		const pushBusy = (from: number, to: number) => {
			if (to - from < 15) return;
			const ids = busyIds(from, to);
			out.push({ from, to, free: false, label: ids.length ? busyNames(ids, members, true) : 'Busy' });
		};
		const good = [...free.map((f) => ({ ...f, most: false })), ...most.map((f) => ({ ...f, most: true }))].sort((a, b) => a.from - b.from);
		for (const f of good) {
			if (f.from > cursor) pushBusy(cursor, f.from);
			if (f.most) {
				out.push({ from: f.from, to: f.to, free: true, most: true, label: missingNote(busyIds(f.from, f.to), members) });
				cursor = Math.max(cursor, f.to);
				continue;
			}
			// A daily window that starts after the axis (e.g. 18:00) means "free from", never "all day".
			const windowLate = range.from > axis.start;
			const whole = !windowLate && f.from <= range.from && f.to >= range.to;
			const label = whole
				? 'Everyone free all day'
				: f.to >= range.to && (f.from > range.from || windowLate)
					? `Free from ${spanText(f.from, f.to).split(' – ')[0]}`
					: `Everyone free · ${spanText(f.from, f.to)}`;
			out.push({ from: f.from, to: f.to, free: true, label });
			cursor = Math.max(cursor, f.to);
		}
		if (cursor < range.to) pushBusy(cursor, range.to);
		return out;
	}
</script>

{#if cards}
	<ul class="cards" aria-label="Days this week">
		{#each dates as d (d)}
			{@const day = days.get(d)}
			{@const parts = day ? blocks(d, day) : []}
			{@const free = parts.filter((b) => b.free && !b.most)}
			{@const most = parts.filter((b) => b.most)}
			{@const busy = parts.filter((b) => !b.free)}
			<li>
				<button type="button" class="card glass {fade(d)}" class:on={d === selected} aria-pressed={d === selected} onclick={() => onselect?.(d)}>
					<span class="date tile {look(d)}" class:keep={d === today}>
						<span class:today={d === today}>{formatDayKey(d).split(' ').slice(0, 2).join(' ')}</span>{#if bankHoliday(d)}<span
								class="bank"
								title={bankHoliday(d)}
							>
								<Icon name="flag" size={11} strokeWidth={2.5} /><span class="sr-only">{bankHoliday(d)}</span>
							</span>{/if}
					</span>
					<span class="info">
						<span class="what" class:free={free.length > 0 || most.length > 0}>
							{#if day === undefined}Loading…{:else if day === null}Outside the timing{:else}{free[0]?.label ??
									(most[0] ? `Most free · ${most[0].label}` : "No time when everyone's free")}{/if}
						</span>
						{#if busy.length}<span class="who">{busy[0].label} busy</span>{/if}
						<span class="strip" aria-hidden="true">
							{#each [...busy, ...most, ...free] as b (b.from + (b.most ? 'm' : b.free ? 'f' : 'b'))}
								<i class:free={b.free} class:most={b.most} style:left="{pct(b.from, axis)}%" style:width="{pct(b.to, axis) - pct(b.from, axis)}%"></i>
							{/each}
						</span>
					</span>
				</button>
			</li>
		{/each}
	</ul>
{:else}
	<div class="week" style:--cols={dates.length}>
		<div class="row heads">
			<span></span>
			{#each dates as d (d)}
				<button
					type="button"
					class="head tile {look(d)} {d === today ? '' : fade(d)}"
					class:on={d === selected}
					aria-pressed={d === selected}
					onclick={() => onselect?.(d)}
					aria-label="See who's free on {d === today ? 'today, ' : ''}{formatDayKey(d)}"
					aria-current={d === today ? 'date' : undefined}
				>
					<span class:today={d === today}>{formatDayKey(d).split(' ').slice(0, 2).join(' ')}</span>
					{#if bankHoliday(d)}<span class="bank" title={bankHoliday(d)}>
							<Icon name="flag" size={11} strokeWidth={2.5} /><span class="sr-only">{bankHoliday(d)}</span>
						</span>{/if}
				</button>
			{/each}
		</div>
		<div class="row body">
			<div class="hours" aria-hidden="true">
				{#each ticks as t (t.label)}<span style:top="{t.at}%">{t.label}</span>{/each}
			</div>
			{#each dates as d (d)}
				{@const day = days.get(d)}
				<!-- Tapping a column picks the day too; the header button above is the keyboard route. -->
				<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_noninteractive_element_interactions -->
				<div class="col {fade(d)}" class:on={d === selected} role="list" aria-label={formatDayKey(d)} onclick={() => onselect?.(d)}>
					{#if day === undefined}
						<span class="state"><Spinner size={16} /></span>
					{:else if day === null}
						<span class="state">Not in timing</span>
					{:else}
						{#each blocks(d, day) as b (b.from)}
							<div
								role="listitem"
								class="block"
								class:free={b.free}
								class:most={b.most}
								style:top="{pct(b.from, axis)}%"
								style:height="{pct(b.to, axis) - pct(b.from, axis)}%"
							>
								<span>{b.label}</span>
								<span class="sr-only">{spanText(b.from, b.to)}</span>
							</div>
						{/each}
					{/if}
				</div>
			{/each}
		</div>
	</div>
{/if}

<style>
	.cards {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.card {
		width: 100%;
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px;
		border-radius: 18px;
		text-align: left;
		color: var(--ink);
	}
	/* Days step back like the month's cells: busy ones half, past and outside ones more. Today's
	   date and the selection ring stay at full strength. */
	.card.dim .date:not(.keep),
	.card.dim .info,
	.head.dim:not(.on),
	.col.dim > * {
		filter: opacity(0.5);
	}
	.card.far .date:not(.keep),
	.card.far .info,
	.head.far:not(.on),
	.col.far > * {
		filter: opacity(0.35);
	}
	/* Each day's date drawn like its month cell. */
	.tile {
		border-radius: 12px;
	}
	.tile.match {
		background: var(--accent);
		color: var(--on-accent);
	}
	.tile.partial {
		background: var(--accent-soft);
		color: var(--accent-ink);
		box-shadow: inset 0 0 0 1.5px var(--accent);
	}
	.tile.busy {
		background: var(--busy-bg);
		box-shadow: inset 0 0 0 1px var(--glass-border);
	}
	.tile.free {
		background: var(--surface);
		box-shadow: inset 0 0 0 1px var(--glass-border);
	}
	.tile.out {
		outline: 1px dashed var(--line-2);
		outline-offset: -1px;
		color: var(--muted);
	}
	.card.on {
		outline: 2px solid var(--ink);
		outline-offset: -2px;
	}
	/* A bank holiday: a small flag beside the date. */
	.bank {
		display: inline-flex;
		margin-left: 4px;
		vertical-align: -1px;
		opacity: 0.75;
	}
	.date {
		width: 56px;
		min-height: 44px;
		flex: none;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0 4px;
		text-align: center;
		font-size: 14px;
		font-weight: 650;
	}
	.info {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 5px;
	}
	.what {
		font-size: 14px;
		font-weight: 600;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.what.free {
		color: var(--accent-ink);
	}
	.who {
		font-size: 12px;
		color: var(--muted);
	}
	.strip {
		position: relative;
		height: 8px;
		border-radius: 4px;
		background: var(--line);
		overflow: hidden;
	}
	.strip i {
		position: absolute;
		top: 0;
		bottom: 0;
		min-width: 3px;
		border-radius: 4px;
		background: color-mix(in srgb, var(--ink) 32%, transparent);
	}
	.strip i.free {
		background: var(--accent);
	}
	.strip i.most {
		background: color-mix(in srgb, var(--accent) 40%, transparent);
	}
	.week {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.row {
		display: grid;
		grid-template-columns: 34px repeat(var(--cols), minmax(0, 1fr));
		gap: 6px;
	}
	.head {
		min-height: 32px;
		border-radius: 10px;
		font-size: 11px;
		font-weight: 600;
		color: var(--muted);
		text-align: center;
	}
	.head.on {
		background: var(--ink);
		color: var(--bg);
	}
	.col.on {
		/* Inside the column, so the ring never reaches the neighbouring days. */
		outline: 2px solid var(--ink);
		outline-offset: -2px;
	}
	.col {
		cursor: pointer;
	}
	.head:hover {
		background: var(--surface);
		color: var(--ink);
	}
	.body {
		height: clamp(320px, 52vh, 520px);
	}
	.hours {
		position: relative;
		font-size: 10px;
		color: var(--muted);
	}
	.hours span {
		position: absolute;
		left: 0;
		transform: translateY(-50%);
	}
	.hours span:first-child {
		transform: none;
	}
	.hours span:last-child {
		transform: translateY(-100%);
	}
	.col {
		position: relative;
		border-radius: 12px;
		background: var(--line);
		margin: 0;
		padding: 0;
	}
	.state {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 11px;
		color: var(--muted);
		text-align: center;
		padding: 6px;
	}
	.block {
		position: absolute;
		left: 3px;
		right: 3px;
		border-radius: 10px;
		padding: 6px;
		font-size: 10px;
		font-weight: 600;
		line-height: 1.25;
		overflow: hidden;
		background: var(--busy-bg);
		opacity: 0.5;
		border: 1px solid var(--busy-line);
		color: var(--ink);
	}
	.block.free {
		background: var(--accent);
		color: var(--on-accent);
		opacity: 1;
		border-color: transparent;
		box-shadow: 0 12px 24px -16px var(--accent);
	}
	/* Free for enough of the group, not everyone: a soft accent block with an accent outline. */
	.block.most {
		background: var(--accent-soft);
		color: var(--accent-ink);
		border: 1.5px solid var(--accent);
		box-shadow: none;
	}
	@media (min-width: 900px) {
		.block {
			font-size: 12px;
			padding: 8px;
		}
		.head {
			font-size: 12px;
		}
	}
	/* Today: its date on an accent pill. */
	.today {
		padding: 1px 7px;
		border-radius: 999px;
		background: var(--accent);
		color: var(--on-accent);
	}
</style>
