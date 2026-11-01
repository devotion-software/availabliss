<script lang="ts">
	import { invalidate } from '$app/navigation';
	import type { ManualBlock } from '#lib/types';
	import { createBlock, deleteBlock } from '#lib/client/blocks';
	import { addDays, startOfWeek } from '#lib/client/dates';
	import { dayNumber, monthTitle, formatDayRange } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';
	import WeekBusyGrid from '#lib/components/mytime/WeekBusyGrid.svelte';
	import BlockRow from '#lib/components/mytime/BlockRow.svelte';
	import BlockTimeSheet from '#lib/components/mytime/BlockTimeSheet.svelte';
	import { toggleableBlock, weekCells, type Cell } from '#lib/components/mytime/parts';

	let { data } = $props();

	const tz = $derived(data.user.timezone);
	const weekEnd = $derived(addDays(data.weekStart, 6));
	const rows = $derived(weekCells(data.weekStart, data.intervals, tz));
	const blockedCount = $derived(rows.flatMap((r) => r.cells).filter((c) => !c.calendar && c.manual).length);
	const weekLabel = $derived(
		data.weekStart.slice(0, 7) === weekEnd.slice(0, 7)
			? `${dayNumber(data.weekStart)} – ${dayNumber(weekEnd)} ${monthTitle(weekEnd).split(' ')[0]}`
			: formatDayRange(data.weekStart, weekEnd, false)
	);
	const isThisWeek = $derived(data.weekStart === startOfWeek(data.today));

	let pending = $state(new Set<string>());
	let sheetOpen = $state(false);
	let editing = $state<ManualBlock | null>(null);
	let deleting = $state<ManualBlock | null>(null);
	let confirmOpen = $state(false);

	const refresh = () => invalidate('app:my-time');

	async function toggle(cell: Cell) {
		const key = `${cell.day}-${cell.part.id}`;
		if (pending.has(key)) return;
		if (cell.manual) {
			const block = toggleableBlock(cell, data.blocks);
			if (!block) {
				toasts.info('This time is part of a longer or repeating block. Edit it in the list below.');
				return;
			}
			pending = new Set(pending).add(key);
			await run(() => deleteBlock(block.id));
		} else {
			pending = new Set(pending).add(key);
			await run(() => createBlock({ start: cell.start, end: cell.end, allDay: false, repeat: 'none', until: null, note: null }));
		}
		const next = new Set(pending);
		next.delete(key);
		pending = next;
	}

	async function run(action: () => Promise<unknown>) {
		try {
			await action();
			await refresh();
		} catch (err) {
			toasts.error(err);
		}
	}

	function openNew() {
		editing = null;
		sheetOpen = true;
	}
	function openEdit(block: ManualBlock) {
		editing = block;
		sheetOpen = true;
	}
	function askDelete(block: ManualBlock) {
		deleting = block;
		confirmOpen = true;
	}
	async function confirmDelete() {
		if (!deleting) return;
		await run(() => deleteBlock(deleting!.id));
	}
</script>

<svelte:head><title>My time · Availabliss</title></svelte:head>

<div class="page">
	<PageHeader title="My time" subtitle="Tap to block time you're busy" backHref="/app" backLabel="Back to groups" backOnPhoneOnly>
		{#snippet actions()}
			<IconButton href="/app/calendars" icon="link" label="Connected calendars" />
		{/snippet}
	</PageHeader>

	<div class="layout">
		<div class="main">
			<div class="week-nav">
				<IconButton
					href="?week={addDays(data.weekStart, -7)}"
					icon="chevron-left"
					label="Previous week"
					shape="rounded"
					size={40}
					data-sveltekit-noscroll
				/>
				<h2 aria-live="polite">{weekLabel}</h2>
				{#if !isThisWeek}
					<Button href="?week={data.today}" variant="ghost" size="sm" data-sveltekit-noscroll>Today</Button>
				{/if}
				<IconButton
					href="?week={addDays(data.weekStart, 7)}"
					icon="chevron-right"
					label="Next week"
					shape="rounded"
					size={40}
					data-sveltekit-noscroll
				/>
			</div>

			<GlassPanel as="section" padding="12px" aria-label="Busy times this week">
				<WeekBusyGrid {rows} today={data.today} {pending} ontoggle={toggle} />
				<div class="legend">
					<span><i class="sw manual"></i>Blocked by you</span>
					<span><i class="sw cal"></i>From a calendar</span>
					<span>{blockedCount} blocked this week</span>
				</div>
			</GlassPanel>
		</div>

		<div class="side">
			<div class="blocks">
				<h2 class="eyebrow">Your blocks</h2>
				{#each data.blocks as block (block.id)}
					<BlockRow {block} timeZone={tz} onedit={() => openEdit(block)} ondelete={() => askDelete(block)} />
				{:else}
					<p class="none">Nothing blocked by hand yet. Tap a slot, or block a specific time.</p>
				{/each}
			</div>
			<Button size="lg" icon="plus" block onclick={openNew}>Block a specific time</Button>
		</div>
	</div>
</div>

<BlockTimeSheet bind:open={sheetOpen} timeZone={tz} block={editing} defaultDay={isThisWeek ? data.today : data.weekStart} onsaved={refresh} />
<ConfirmSheet
	bind:open={confirmOpen}
	title="Delete this block?"
	message={deleting?.repeat !== 'none' ? 'Every repeat of it will be removed.' : 'This time will count as free again.'}
	confirmLabel="Delete"
	destructive
	onconfirm={confirmDelete}
/>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.layout {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.main,
	.side,
	.blocks {
		display: flex;
		flex-direction: column;
		gap: 10px;
		min-width: 0;
	}
	.side {
		gap: 14px;
	}
	.week-nav {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.week-nav h2 {
		flex: 1;
		text-align: center;
		font-size: 17px;
		font-weight: 650;
	}
	.legend {
		display: flex;
		flex-wrap: wrap;
		gap: 12px;
		padding-top: 10px;
		font-size: 11px;
		color: var(--muted);
	}
	.legend span {
		display: flex;
		align-items: center;
		gap: 5px;
	}
	.sw {
		width: 12px;
		height: 10px;
		border-radius: 3px;
	}
	.sw.manual {
		background: repeating-linear-gradient(135deg, var(--accent) 0 2px, var(--accent-soft) 2px 5px);
	}
	.sw.cal {
		background: var(--busy-bg);
		opacity: 0.5;
		border: 1px solid var(--busy-line);
	}
	.none {
		font-size: 13px;
		color: var(--muted);
		padding: 0 4px;
	}
	@media (min-width: 900px) {
		.layout {
			display: grid;
			grid-template-columns: minmax(0, 1fr) minmax(280px, 360px);
			gap: 20px;
			align-items: start;
		}
		.week-nav h2 {
			font-size: 20px;
		}
	}
</style>
