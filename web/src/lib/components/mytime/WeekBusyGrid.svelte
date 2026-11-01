<script lang="ts">
	import { dayNumber, weekdayName } from '#lib/client/format';
	import { PARTS, type Cell } from './parts';

	/** Days × morning / afternoon / evening. Calendar busy is read-only; your own blocks toggle. */
	interface Props {
		rows: { day: string; cells: Cell[] }[];
		today: string;
		pending: ReadonlySet<string>;
		ontoggle: (cell: Cell) => void;
	}
	let { rows, today, pending, ontoggle }: Props = $props();

	const key = (c: Cell) => `${c.day}-${c.part.id}`;

	function aria(c: Cell): string {
		const base = `${weekdayName(c.day, 'long')} ${dayNumber(c.day)} ${c.part.label.toLowerCase()}`;
		if (c.calendar) return `${base}, busy from calendar${c.calendar.label ? `: ${c.calendar.label}` : ''}`;
		if (c.manual) return `${base}, blocked by you`;
		return `${base}, free`;
	}
</script>

<div class="grid" role="group" aria-label="Busy times this week">
	<div class="row head" aria-hidden="true">
		<span></span>
		{#each PARTS as p (p.id)}<span>{p.label}</span>{/each}
	</div>
	{#each rows as row (row.day)}
		<div class="row">
			<span class="day" class:today={row.day === today}>
				<span class="dname">{weekdayName(row.day)}</span>
				<span class="dnum">{dayNumber(row.day)}</span>
			</span>
			{#each row.cells as cell (key(cell))}
				{@const blocked = !cell.calendar && !!cell.manual}
				<button
					type="button"
					class="slot"
					class:cal={!!cell.calendar}
					class:blocked
					class:pending={pending.has(key(cell))}
					aria-pressed={cell.calendar ? undefined : blocked}
					aria-disabled={cell.calendar ? 'true' : undefined}
					aria-label={aria(cell)}
					onclick={() => !cell.calendar && ontoggle(cell)}
				>
					{#if cell.calendar}
						<span class="dot" aria-hidden="true"></span>
						<span class="label">{cell.calendar.label ?? 'Busy'}</span>
					{:else if blocked}
						<span class="label">{cell.manual?.label ?? 'Busy'}</span>
					{/if}
				</button>
			{/each}
		</div>
	{/each}
</div>

<style>
	.grid {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.row {
		display: grid;
		grid-template-columns: 50px repeat(3, minmax(0, 1fr));
		gap: 6px;
		align-items: center;
	}
	.head {
		font-size: 11px;
		font-weight: 600;
		color: var(--muted);
		text-align: center;
	}
	.day {
		display: flex;
		flex-direction: column;
		line-height: 1.15;
	}
	.dname {
		font-size: 12px;
		font-weight: 600;
	}
	.dnum {
		font-size: 11px;
		color: var(--muted);
	}
	.today .dname,
	.today .dnum {
		color: var(--accent-ink);
		font-weight: 700;
	}
	.slot {
		height: 46px;
		min-width: 0;
		border-radius: 12px;
		padding: 0 6px;
		font-size: 11px;
		font-weight: 600;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 4px;
		background: var(--surface);
		border: 1px solid var(--glass-border);
		color: var(--muted);
		transition:
			transform 0.12s ease,
			background-color 0.2s ease;
	}
	.slot:active:not(.cal) {
		transform: scale(0.96);
	}
	.slot:hover:not(.cal):not(.blocked) {
		border-color: var(--line-3);
	}
	.cal {
		background: var(--busy-bg);
		border-color: var(--busy-line);
		opacity: 0.5;
		color: var(--ink);
		cursor: default;
	}
	.blocked {
		background: var(--stripe-manual), var(--surface);
		border: 1.5px solid var(--accent);
		color: var(--accent-ink);
	}
	.pending {
		opacity: 0.6;
	}
	.dot {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		flex: none;
		background: var(--accent);
	}
	.label {
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	@media (min-width: 900px) {
		.row {
			grid-template-columns: 64px repeat(3, minmax(0, 1fr));
			gap: 8px;
		}
		.slot {
			height: 52px;
			font-size: 12px;
		}
	}
</style>
