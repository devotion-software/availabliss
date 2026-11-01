<script lang="ts">
	/**
	 * A static, decorative group calendar (Web.dc.html): only the weekday columns
	 * the timing can use, a joined match band for weekends that work, faded busy days.
	 */
	const COLS = [4, 5, 6];
	const NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Friday', 'Saturday', 'Sunday'];
	const BUSY: Record<number, string> = { 7: 'Priya busy', 20: 'Tom busy' };
	const MATCH: Record<number, 's' | 'm' | 'e'> = { 13: 's', 14: 'm', 15: 'e', 27: 's', 28: 'm', 29: 'e' };

	const cells = Array.from({ length: 5 * COLS.length }, (_, i) => {
		const d = Math.floor(i / COLS.length) * 7 + COLS[i % COLS.length] - 5;
		const show = d >= 1 && d <= 30;
		const match = MATCH[d];
		return {
			d,
			show,
			kind: match ? 'match' : BUSY[d] ? 'busy' : 'free',
			pos: match ?? '',
			note: match === 's' ? 'Free from 18:00' : match ? 'Everyone free' : (BUSY[d] ?? (d === 1 ? 'Today' : ''))
		};
	});
</script>

<div class="mock glass" aria-hidden="true">
	<div class="top">
		<span class="title">November 2026</span>
		<span class="sync"><b>4 of 6 synced</b> · Leo, Jade to go</span>
	</div>
	<div class="cols">
		{#each COLS as c (c)}<span>{NAMES[c]}</span>{/each}
	</div>
	<div class="grid">
		{#each cells as cell, i (i)}
			<div class="cell {cell.kind} {cell.pos}" class:hide={!cell.show} style:--i={i}>
				<span class="d">{cell.show ? cell.d : ''}</span>
				<span class="note">{cell.show ? cell.note : ''}</span>
			</div>
		{/each}
	</div>
	<div class="legend">
		<span><i class="sw m"></i>Works for everyone</span>
		<span><i class="sw b"></i>Someone's busy</span>
	</div>
</div>

<style>
	.mock {
		border-radius: 28px;
		padding: 18px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 10px;
		flex-wrap: wrap;
	}
	.title {
		font-family: var(--font-display);
		letter-spacing: -0.025em;
		font-size: 20px;
		font-weight: 650;
	}
	.sync {
		font-size: 12px;
		padding: 6px 12px;
		border-radius: 999px;
		background: var(--warn-bg);
		color: var(--warn-ink);
	}
	.sync b {
		font-weight: 600;
	}
	.cols,
	.grid {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 6px;
	}
	.cols {
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
	}
	.cols span {
		padding-left: 6px;
	}
	.cell {
		min-height: 68px;
		padding: 10px;
		border-radius: 16px;
		display: flex;
		flex-direction: column;
		justify-content: space-between;
		animation: cell-in 0.6s var(--ease-spring) both;
		animation-delay: calc(var(--i) * 35ms + 200ms);
	}
	.hide {
		visibility: hidden;
	}
	.d {
		font-size: 15px;
		font-weight: 650;
	}
	.note {
		font-size: 11px;
		font-weight: 600;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.free {
		background: var(--surface);
		border: 1px solid var(--glass-border);
	}
	.busy {
		background: var(--busy-bg);
		border: 1px solid var(--glass-border);
		opacity: 0.5;
	}
	.match {
		background: var(--accent);
		color: var(--on-accent);
		box-shadow: 0 14px 28px -16px var(--accent);
		border-radius: 6px;
	}
	.match.s {
		border-radius: 18px 6px 6px 18px;
	}
	.match.e {
		border-radius: 6px 18px 18px 6px;
	}
	.legend {
		display: flex;
		flex-wrap: wrap;
		gap: 16px;
		font-size: 12px;
		color: var(--muted);
	}
	.legend span {
		display: flex;
		align-items: center;
		gap: 6px;
	}
	.sw {
		width: 12px;
		height: 12px;
		border-radius: 4px;
	}
	.sw.m {
		background: var(--accent);
	}
	.sw.b {
		background: var(--busy-bg);
		opacity: 0.5;
		border: 1px solid var(--busy-line);
	}
	@keyframes cell-in {
		from {
			opacity: 0;
			transform: translateY(8px) scale(0.96);
		}
	}
	@media (max-width: 480px) {
		.cell {
			min-height: 54px;
			padding: 8px;
		}
		.note {
			font-size: 10px;
		}
	}
</style>
