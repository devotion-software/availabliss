<script lang="ts">
	import type { DayAvailability, Member } from '#lib/types';
	import { formatDayKey, useFormat } from '#lib/client/format';
	import { memberLabel } from '#lib/client/members';
	import { axisFor, clampToAxis, hourTicks, pct, spanText } from './timeline';

	/** Who's busy when on one day: an "Everyone" row of shared free time, then a row per member. */
	interface Props {
		day: DayAvailability;
		members: Map<string, Member>;
		meId: string;
	}
	let { day, members, meId }: Props = $props();

	const fmt = useFormat();
	const tz = $derived(fmt.current.timeZone);
	const axis = $derived(
		axisFor(day.date, day.members.flatMap((m) => m.busy), tz)
	);
	const ticks = $derived(hourTicks(axis));
	const windowStart = $derived(day.window ? fmt.current.time(day.window.start) : null);

	const free = $derived(day.everyoneFree.map((i) => clampToAxis(i, day.date, axis, tz)).filter((x) => x !== null));
	// You first, then synced members, then people who haven't synced.
	const rows = $derived(
		[...day.members]
			.sort((a, b) => Number(b.userId === meId) - Number(a.userId === meId) || Number(a.pending) - Number(b.pending))
			.map((m) => ({
				...m,
				name: memberLabel(members, m.userId, meId, { first: true }),
				bars: m.busy
					.map((b) => ({ manual: b.manual, span: clampToAxis(b, day.date, axis, tz) }))
					.filter((b) => b.span !== null)
			}))
	);

	const style = (from: number, to: number) => `left:${pct(from, axis)}%;width:${pct(to, axis) - pct(from, axis)}%`;
</script>

<div class="timeline">
	<div class="meta">
		<span class="date">{formatDayKey(day.date)}</span>
		{#if windowStart && windowStart !== '00:00'}<span class="muted">Timing starts {windowStart}</span>{/if}
	</div>

	<div class="rows">
		<span class="who everyone">Everyone</span>
		<div class="track shared">
			{#each free as f (f.from)}
				<div class="free" style={style(f.from, f.to)}><span>{spanText(f.from, f.to)}</span></div>
			{:else}
				<span class="none">No time when everyone's free</span>
			{/each}
		</div>

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
					<div class="busy" class:manual={b.manual} style={style(b.span!.from, b.span!.to)}></div>
				{/each}
				{#if m.pending}<span class="none">Not synced yet</span>{/if}
			</div>
		{/each}

		<span></span>
		<div class="ticks" aria-hidden="true">
			{#each ticks as t (t.label)}<span style:left="{t.at}%">{t.label}</span>{/each}
		</div>
	</div>

	<span class="legend"><span class="key"></span>Striped = blocked by hand</span>
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
		background: var(--accent-soft);
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
	.busy {
		position: absolute;
		top: 0;
		bottom: 0;
		border-radius: 7px;
		opacity: 0.5;
		background: var(--busy-bg);
		border: 1px solid var(--busy-line);
	}
	.busy.manual {
		background: var(--stripe-group);
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
		align-items: center;
		gap: 6px;
		font-size: 11px;
		color: var(--muted);
	}
	.key {
		width: 14px;
		height: 10px;
		border-radius: 3px;
		background: repeating-linear-gradient(135deg, var(--line-3) 0 2px, transparent 2px 5px);
	}
</style>
