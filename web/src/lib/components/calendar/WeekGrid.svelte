<script lang="ts">
	import type { DayAvailability, Member } from '#lib/types';
	import { formatDayKey, useFormat } from '#lib/client/format';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import { busyNames } from './grid';
	import { axisFor, clampToAxis, hourTicks, mergeAxes, pct, spanText, type Axis } from './timeline';

	/** A week of the group's visible days: shared free time as accent blocks, everything else as faded busy blocks. */
	interface Props {
		dates: readonly string[];
		/** Day detail per date; missing = still loading, null = outside the timing. */
		days: ReadonlyMap<string, DayAvailability | null>;
		members: Map<string, Member>;
		onselect?: (date: string) => void;
	}
	let { dates, days, members, onselect }: Props = $props();

	const fmt = useFormat();
	const tz = $derived(fmt.current.timeZone);

	const axis: Axis = $derived(
		mergeAxes(
			dates.flatMap((d) => {
				const day = days.get(d);
				return day ? [axisFor(d, day.members.flatMap((m) => m.busy), tz)] : [];
			})
		)
	);
	const ticks = $derived(hourTicks(axis));

	interface Block {
		from: number;
		to: number;
		free: boolean;
		label: string;
	}

	function blocks(date: string, day: DayAvailability): Block[] {
		const range = day.window ? clampToAxis(day.window, date, axis, tz) : { from: axis.start, to: axis.end };
		if (!range) return [];
		const free = day.everyoneFree
			.map((i) => clampToAxis(i, date, axis, tz))
			.filter((x) => x !== null)
			.sort((a, b) => a.from - b.from);
		const out: Block[] = [];
		let cursor = range.from;
		const pushBusy = (from: number, to: number) => {
			if (to - from < 15) return;
			const ids = day.members
				.filter((m) => m.busy.some((b) => {
					const s = clampToAxis(b, date, axis, tz);
					return s && s.from < to && s.to > from;
				}))
				.map((m) => m.userId);
			out.push({ from, to, free: false, label: ids.length ? busyNames(ids, members, true) : 'Busy' });
		};
		for (const f of free) {
			if (f.from > cursor) pushBusy(cursor, f.from);
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

<div class="week" style:--cols={dates.length}>
	<div class="row heads">
		<span></span>
		{#each dates as d (d)}
			<button type="button" class="head" onclick={() => onselect?.(d)} aria-label="See who's free on {formatDayKey(d)}">
				{formatDayKey(d).split(' ').slice(0, 2).join(' ')}
			</button>
		{/each}
	</div>
	<div class="row body">
		<div class="hours" aria-hidden="true">
			{#each ticks as t (t.label)}<span style:top="{t.at}%">{t.label}</span>{/each}
		</div>
		{#each dates as d (d)}
			{@const day = days.get(d)}
			<div class="col" role="list" aria-label={formatDayKey(d)}>
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

<style>
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
	@media (min-width: 900px) {
		.block {
			font-size: 12px;
			padding: 8px;
		}
		.head {
			font-size: 12px;
		}
	}
</style>
