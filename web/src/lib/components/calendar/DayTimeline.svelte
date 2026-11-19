<script lang="ts">
	import type { DayAvailability, Member } from '#lib/types';
	import { formatDayKey, useFormat } from '#lib/client/format';
	import { memberLabel } from '#lib/client/members';
	import { clampToAxis, hourTicks, pct, spanText, type Axis } from './timeline';

	/**
	 * Who's busy when on one day: an "Everyone" row of shared free time, a "Most free" row when the
	 * group has a quorum or optional members, then a row per member.
	 */
	interface Props {
		day: DayAvailability;
		members: Map<string, Member>;
		meId: string;
		/** Off inside the day card, which already names the day. */
		showDate?: boolean;
	}
	let { day, members, meId, showDate = true }: Props = $props();

	const fmt = useFormat();
	const tz = $derived(fmt.current.timeZone);
	// The whole day, so early and late busy time is never cut off.
	const axis: Axis = { start: 0, end: 24 * 60 };
	const ticks = $derived(hourTicks(axis));
	const windowStart = $derived(day.window ? fmt.current.time(day.window.start) : null);

	const free = $derived(day.everyoneFree.map((i) => clampToAxis(i, day.date, axis, tz)).filter((x) => x !== null));
	// When enough of the group is free (includes when everyone is). Only with a quorum or optional members.
	const most = $derived(day.mostFree ? day.mostFree.map((i) => clampToAxis(i, day.date, axis, tz)).filter((x) => x !== null) : null);
	// You first, then synced members, then people who haven't synced.
	const rows = $derived(
		[...day.members]
			.sort((a, b) => Number(b.userId === meId) - Number(a.userId === meId) || Number(a.pending) - Number(b.pending))
			.map((m) => ({
				...m,
				name: memberLabel(members, m.userId, meId, { first: true }),
				bars: m.busy.map((b) => ({ span: clampToAxis(b, day.date, axis, tz) })).filter((b) => b.span !== null)
			}))
	);

	// The Everyone track's width. A band's times go inside it only if every band is wide enough
	// (about 6.2px a character at 11px, plus padding); otherwise they go on a line under the row.
	let trackWidth = $state(0);
	const fits = (spans: { from: number; to: number }[]) =>
		trackWidth === 0 || spans.every((f) => ((f.to - f.from) / (axis.end - axis.start)) * trackWidth >= spanText(f.from, f.to).length * 6.2 + 12);
	const freeInside = $derived(fits(free));
	const mostInside = $derived(most ? fits(most) : true);

	const style = (from: number, to: number) => `left:${pct(from, axis)}%;width:${pct(to, axis) - pct(from, axis)}%`;
</script>

<div class="timeline">
	<div class="meta">
		{#if showDate}<span class="date">{formatDayKey(day.date)}</span>{/if}
		{#if windowStart && windowStart !== '00:00'}<span class="muted">Timing starts {windowStart}</span>{/if}
	</div>

	<div class="rows">
		<span class="who everyone">Everyone</span>
		<div class="track shared" bind:clientWidth={trackWidth}>
			{#each free as f (f.from)}
				<div class="free" style={style(f.from, f.to)}>
					{#if freeInside}<span>{spanText(f.from, f.to)}</span>{/if}
				</div>
			{:else}
				<span class="none">No time when everyone's free</span>
			{/each}
		</div>
		{#if !freeInside}
			<span></span>
			<span class="times">{free.map((f) => spanText(f.from, f.to)).join(' · ')}</span>
		{/if}

		{#if most}
			<span class="who most-who">Most free</span>
			<div
				class="track shared"
				role="img"
				aria-label={most.length
					? `Most of the group free ${most.map((f) => spanText(f.from, f.to)).join(', ')}`
					: 'No time when most of the group is free'}
			>
				{#each most as f (f.from)}
					<div class="free most" style={style(f.from, f.to)}>
						{#if mostInside}<span>{spanText(f.from, f.to)}</span>{/if}
					</div>
				{:else}
					<span class="none">No time when most are free</span>
				{/each}
			</div>
			{#if !mostInside}
				<span></span>
				<span class="times most-times">{most.map((f) => spanText(f.from, f.to)).join(' · ')}</span>
			{/if}
		{/if}

		{#each rows as m (m.userId)}
			<span class="who">{m.name}</span>
			<div
				class="track"
				class:pending={m.pending}
				role="img"
				aria-label={m.pending
					? `${m.name}: not synced yet`
					: m.bars.length
						? `${m.name}: busy ${m.bars.map((b) => spanText(b.span!.from, b.span!.to)).join(', ')}`
						: `${m.name}: free all day`}
			>
				{#each m.bars as b, i (i)}
					<div class="busy" style={style(b.span!.from, b.span!.to)}></div>
				{/each}
				{#if m.pending}<span class="none">Not synced yet</span>{/if}
			</div>
		{/each}

		<span></span>
		<div class="ticks" aria-hidden="true">
			{#each ticks as t (t.label)}<span style:left="{t.at}%">{t.label}</span>{/each}
		</div>
	</div>

	<div class="legend" aria-hidden="true">
		<span><i class="key free-key"></i>Free</span>
		<span><i class="key busy-key"></i>Busy</span>
		<span><i class="key all-key"></i>Everyone free</span>
		{#if most}<span><i class="key most-key"></i>Most free</span>{/if}
	</div>
</div>

<style>
	.timeline {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.meta {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
		gap: 8px;
	}
	.date {
		font-size: 14px;
		font-weight: 600;
	}
	.muted {
		font-size: 12px;
	}
	.rows {
		display: grid;
		grid-template-columns: 76px minmax(0, 1fr);
		gap: 8px;
		align-items: center;
	}
	.who {
		font-size: 12px;
		font-weight: 500;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.everyone {
		font-weight: 700;
		color: var(--accent-ink);
	}
	.track {
		position: relative;
		height: 22px;
		border-radius: 7px;
		background: var(--ok-bg);
	}
	.track.shared {
		height: 30px;
		border-radius: 9px;
		background: var(--line);
	}
	.track.pending {
		background: transparent;
		border: 1px dashed var(--line-3);
	}
	.free {
		position: absolute;
		top: 0;
		bottom: 0;
		border-radius: 9px;
		background: var(--accent);
		color: var(--on-accent);
		font-size: 11px;
		font-weight: 600;
		display: flex;
		align-items: center;
		padding-left: 8px;
		overflow: hidden;
		white-space: nowrap;
	}
	/* Enough of the group free, not necessarily everyone: a lighter band. */
	.free.most {
		background: var(--accent-soft);
		color: var(--accent-ink);
		box-shadow: inset 0 0 0 1.5px var(--accent);
	}
	/* Times for bands too narrow to hold them, under their row. */
	.times {
		margin-top: -4px;
		font-size: 11px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	.most-times {
		color: var(--muted);
	}
	.most-who {
		font-weight: 600;
		color: var(--accent-ink);
	}
	.busy {
		position: absolute;
		top: 0;
		bottom: 0;
		min-width: 2px;
		border-radius: 7px;
		background: var(--busy-fill);
	}
	.none {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		padding-left: 8px;
		font-size: 10px;
		color: var(--muted);
	}
	.ticks {
		position: relative;
		height: 14px;
		font-size: 10px;
		color: var(--muted);
	}
	.ticks span {
		position: absolute;
		transform: translateX(-50%);
	}
	.ticks span:first-child {
		transform: none;
	}
	.ticks span:last-child {
		transform: translateX(-100%);
	}
	.legend {
		display: flex;
		gap: 14px;
		font-size: 11px;
		color: var(--muted);
	}
	.legend span {
		display: inline-flex;
		align-items: center;
		gap: 5px;
	}
	.key {
		width: 12px;
		height: 10px;
		border-radius: 3px;
	}
	.free-key {
		background: var(--ok-bg);
	}
	.busy-key {
		background: var(--busy-fill);
	}
	.all-key {
		background: var(--accent);
	}
	.most-key {
		background: var(--accent-soft);
		box-shadow: inset 0 0 0 1.5px var(--accent);
	}
	.timeline {
		/* Someone's busy time: clearly darker than the mint free track. */
		--busy-fill: color-mix(in srgb, var(--ink) 32%, transparent);
	}
</style>
