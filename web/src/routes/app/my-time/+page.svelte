<script lang="ts">
	import { tick } from 'svelte';
	import { MediaQuery } from 'svelte/reactivity';
	import { invalidate } from '$app/navigation';
	import type { BusyInterval, ManualBlock } from '#lib/types';
	import { createBlock, deleteBlock } from '#lib/client/blocks';
	import { getMyBusy } from '#lib/client/me';
	import { addDays, eachDay, minutesIntoDay, zonedToIso } from '#lib/client/dates';
	import { weekdayName } from '#lib/client/format';
	import { live } from '#lib/client/live.svelte';
	import { toasts } from '#lib/client/toast.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon, { type IconName } from '#lib/components/ui/Icon.svelte';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';
	import BlockRow from '#lib/components/mytime/BlockRow.svelte';
	import BlockTimeSheet from '#lib/components/mytime/BlockTimeSheet.svelte';
	import CalendarBusySheet from '#lib/components/mytime/CalendarBusySheet.svelte';
	import DayRow from '#lib/components/mytime/DayRow.svelte';
	import DayDetail from '#lib/components/mytime/DayDetail.svelte';
	import { clock, layoutDays, rangeToIso } from '#lib/components/mytime/parts';
	import { CHUNK_DAYS, eveningPreset, groupWeeks, longDate } from '#lib/components/mytime/days';

	let { data } = $props();

	const tz = $derived(data.user.timezone);
	const today = $derived(data.today);
	/** How far ahead the list (and "Jump to date") goes. */
	const MAX_DAYS = 366;
	const wide = new MediaQuery('(min-width: 900px)', false);

	// ── Days, loaded a few weeks at a time ─────────────────────────────────
	/** Chunks after the first (which comes with the page), each CHUNK_DAYS long. */
	let more = $state<{ start: string; intervals: BusyInterval[] }[]>([]);
	let loadingMore = $state(false);
	let moreFor = '';
	// A new day (after midnight) starts the list again.
	$effect.pre(() => {
		if (moreFor !== today) {
			moreFor = today;
			more = [];
		}
	});

	const end = $derived(addDays(today, CHUNK_DAYS * (1 + more.length)));
	const days = $derived(eachDay(today, addDays(end, -1)));
	const intervals = $derived.by(() => {
		// Intervals crossing a chunk edge come back in both chunks.
		const seen = new Set<string>();
		const out: BusyInterval[] = [];
		for (const i of [...data.intervals, ...more.flatMap((c) => c.intervals)]) {
			const key = `${i.source}|${i.start}|${i.end}|${i.blockId ?? i.calendarId}|${i.eventKey ?? ''}`;
			if (seen.has(key)) continue;
			seen.add(key);
			out.push(i);
		}
		return out;
	});
	const layout = $derived(layoutDays(days, intervals, tz));
	const weeks = $derived(groupWeeks(days, today));
	const repeating = $derived(data.blocks.filter((b) => b.repeat !== 'none'));
	const atLimit = $derived(days.length >= MAX_DAYS);

	const fetchChunk = (start: string) =>
		getMyBusy(zonedToIso(start, '00:00', tz), zonedToIso(addDays(start, CHUNK_DAYS), '00:00', tz)).then((intervals) => ({ start, intervals }));

	async function loadMore() {
		if (loadingMore || atLimit) return;
		loadingMore = true;
		try {
			more = [...more, await fetchChunk(end)];
		} catch (err) {
			toasts.error(err);
		} finally {
			loadingMore = false;
		}
	}

	/** Loads chunks until `day` is in the list. */
	async function ensureLoaded(day: string) {
		while (day >= end && !atLimit) {
			const before = more.length;
			await loadMore();
			if (more.length === before) return;
		}
	}

	// Load the next weeks as the end of the list comes into view.
	let sentinel = $state<HTMLElement>();
	$effect(() => {
		if (!sentinel) return;
		const observer = new IntersectionObserver((entries) => entries.some((e) => e.isIntersecting) && loadMore(), { rootMargin: '600px 0px' });
		observer.observe(sentinel);
		return () => observer.disconnect();
	});

	async function refresh() {
		const starts = more.map((c) => c.start);
		const [, chunks] = await Promise.all([invalidate('app:my-time'), Promise.all(starts.map(fetchChunk))]);
		if (moreFor === today) more = chunks;
	}

	// Your busy time changed elsewhere (another device, a calendar sync): refetch. Bursts collapse into one.
	$effect(() => {
		const me = data.user.id;
		let timer: ReturnType<typeof setTimeout> | undefined;
		const soon = () => {
			clearTimeout(timer);
			timer = setTimeout(() => refresh().catch(() => {}), 600);
		};
		const off = live.on(['availability.changed'], (e) => {
			if ((e.payload as { userId?: string } | undefined)?.userId === me) soon();
		});
		const offReconnect = live.onReconnect(soon);
		return () => {
			off();
			offReconnect();
			clearTimeout(timer);
		};
	});

	async function run(action: () => Promise<unknown>) {
		try {
			await action();
			await refresh();
		} catch (err) {
			toasts.error(err);
		}
	}

	// ── The day sheet (phones) / panel (wide screens) ──────────────────────
	let selectedDay = $state('');
	const shownDay = $derived(selectedDay && selectedDay >= today ? selectedDay : today);
	let dayOpen = $state(false);
	const shownLayout = $derived(shownDay < end ? layout : layoutDays([shownDay], intervals, tz));

	function openDay(day: string) {
		selectedDay = day;
		if (!wide.current) dayOpen = true;
	}
	async function stepDay(by: number) {
		const day = addDays(shownDay, by);
		if (day < today) return;
		selectedDay = day;
		await ensureLoaded(day);
	}

	// ── Blocking time ──────────────────────────────────────────────────────
	let sheetOpen = $state(false);
	let editing = $state<ManualBlock | null>(null);
	let sheetDay = $state('');
	let sheetRange = $state<{ from: number; to: number } | null>(null);
	let sheetAllDay = $state(false);
	let sheetLastDay = $state<string | null>(null);

	function openNew(day: string, opts: { range?: { from: number; to: number } | null; allDay?: boolean; lastDay?: string | null } = {}) {
		editing = null;
		sheetDay = day;
		sheetRange = opts.range ?? null;
		sheetAllDay = opts.allDay ?? false;
		sheetLastDay = opts.lastDay ?? null;
		sheetOpen = true;
	}

	/** A mouse drag blocks that exact time straight away; a tap opens the sheet on that hour. */
	async function select(day: string, from: number, to: number, dragged: boolean) {
		if (!dragged) return openNew(day, { range: { from, to } });
		await run(async () => {
			await createBlock({ ...rangeToIso(day, from, to, tz), allDay: false, repeat: 'none', until: null, note: null });
			toasts.success(`Blocked ${weekdayName(day)} ${clock(from)} – ${clock(to)}`);
		});
	}

	/** Tap on busy time: edit your own block, or explain calendar time. */
	let calendarOpen = $state(false);
	let calendarInterval = $state<BusyInterval | null>(null);
	function open(interval: BusyInterval) {
		if (interval.source === 'calendar') {
			calendarInterval = interval;
			calendarOpen = true;
			return;
		}
		const block = data.blocks.find((b) => b.id === interval.blockId);
		if (block) openEdit(block);
		else toasts.info('That block was just removed.');
	}
	function openEdit(block: ManualBlock) {
		editing = block;
		sheetOpen = true;
	}

	// "Block time": quick choices, each opening the block sheet ready to go.
	let choicesOpen = $state(false);
	const choices = $derived.by(() => {
		void choicesOpen; // Recomputed on each open, so "This evening" follows the clock.
		const now = minutesIntoDay(new Date().toISOString(), today, tz);
		const evening = eveningPreset(today, now);
		const list: { label: string; sub: string; icon: IconName; go: () => void }[] = [
			{
				label: evening.day === today ? 'This evening' : 'Tomorrow evening',
				sub: `${weekdayName(evening.day)} from 18:00`,
				icon: 'moon',
				go: () => openNew(evening.day, { range: evening.range })
			},
			{ label: 'All day…', sub: 'Pick a day', icon: 'sun', go: () => openNew(today, { allDay: true }) },
			{ label: 'Away for a few days…', sub: 'Pick the first and last day', icon: 'map-pin', go: () => openNew(today, { allDay: true, lastDay: addDays(today, 2) }) },
			{ label: 'Something else…', sub: 'Choose the times, or make it repeat', icon: 'clock', go: () => openNew(shownDay) }
		];
		return list;
	});
	function choose(go: () => void) {
		choicesOpen = false;
		go();
	}

	// ── Repeating blocks ───────────────────────────────────────────────────
	let deleting = $state<ManualBlock | null>(null);
	let confirmOpen = $state(false);
	function askDelete(block: ManualBlock) {
		deleting = block;
		confirmOpen = true;
	}
	async function confirmDelete() {
		if (!deleting) return;
		await run(() => deleteBlock(deleting!.id));
	}

	// ── Jump to a date ─────────────────────────────────────────────────────
	let jumpOpen = $state(false);
	let jumpDate = $state('');
	let jumping = $state(false);
	function openJump() {
		jumpDate = shownDay;
		jumpOpen = true;
	}
	async function jump(e: SubmitEvent) {
		e.preventDefault();
		const last = addDays(today, MAX_DAYS - 1);
		const day = jumpDate < today ? today : jumpDate > last ? last : jumpDate;
		if (!day) return;
		jumping = true;
		await ensureLoaded(day);
		jumping = false;
		jumpOpen = false;
		selectedDay = day;
		await tick();
		document.getElementById(`day-${day}`)?.scrollIntoView({ block: 'center', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
	}

	const showCalendarHint = $derived(data.hasCalendar === false && !intervals.some((i) => i.source === 'calendar'));
</script>

<svelte:head><title>My time · Availabliss</title></svelte:head>

{#snippet everyWeek()}
	<section class="every-week" aria-labelledby="every-week">
		<h2 class="eyebrow" id="every-week">Every week</h2>
		{#each repeating as block (block.id)}
			<BlockRow {block} timeZone={tz} onedit={() => openEdit(block)} ondelete={() => askDelete(block)} />
		{:else}
			<p class="none">Nothing repeats yet. Block time and choose Weekly for things like a class or practice.</p>
		{/each}
	</section>
{/snippet}

<div class="page">
	<PageHeader title="My time" subtitle="What groups see as busy" backHref="/app" backLabel="Home" backIcon="home">
		{#snippet actions()}
			<IconButton icon="calendar-search" label="Jump to a date" onclick={openJump} />
			<IconButton href="/app/calendars" icon="calendar-sync" label="Connected calendars" />
		{/snippet}
	</PageHeader>

	<div class="layout">
		<div class="list-col">
			<Button size="lg" variant="accent" icon="plus" block onclick={() => (choicesOpen = true)}>Block time</Button>

			{#if showCalendarHint}
				<a class="glass hint" href="/app/calendars">
					<span class="hint-icon"><Icon name="calendar-sync" size={18} /></span>
					<span class="hint-text">Connect a calendar so your busy time fills in by itself</span>
					<Icon name="chevron-right" size={16} />
				</a>
			{/if}

			{#each weeks as week, w (week.start)}
				<section class="week" aria-labelledby="week-{week.start}">
					<h2 class="eyebrow" id="week-{week.start}">{week.heading}</h2>
					<div class="glass days">
						{#each week.days as day (day)}
							<div class="day" id="day-{day}">
								<DayRow
									{day}
									{today}
									timed={layout.timed.get(day) ?? []}
									allDay={layout.allDay.get(day) ?? []}
									selected={wide.current && day === shownDay}
									onclick={() => openDay(day)}
								/>
							</div>
						{/each}
					</div>
				</section>
				{#if w === 0}{@render everyWeek()}{/if}
			{/each}

			<div class="end" bind:this={sentinel}>
				{#if loadingMore}
					<Spinner size={20} />
				{:else if atLimit}
					<span>That's a year ahead</span>
				{:else}
					<Button variant="ghost" size="sm" onclick={loadMore}>Show more weeks</Button>
				{/if}
			</div>
		</div>

		<aside class="panel-col" aria-label="Day">
			<GlassPanel padding="16px" class="day-panel">
				<DayDetail
					day={shownDay}
					{today}
					timeZone={tz}
					title={longDate(shownDay)}
					timed={shownLayout.timed.get(shownDay) ?? []}
					allDay={shownLayout.allDay.get(shownDay) ?? []}
					atStart={shownDay <= today}
					onprev={() => stepDay(-1)}
					onnext={() => stepDay(1)}
					onselect={select}
					onopen={open}
				/>
			</GlassPanel>
		</aside>
	</div>
</div>

<Sheet bind:open={dayOpen} title={longDate(shownDay)} tall>
	<DayDetail
		day={shownDay}
		{today}
		timeZone={tz}
		timed={shownLayout.timed.get(shownDay) ?? []}
		allDay={shownLayout.allDay.get(shownDay) ?? []}
		atStart={shownDay <= today}
		onprev={() => stepDay(-1)}
		onnext={() => stepDay(1)}
		onselect={select}
		onopen={open}
	/>
</Sheet>

<Sheet bind:open={choicesOpen} title="Block time" width={420}>
	<div class="choices">
		{#each choices as c (c.label)}
			<button type="button" class="choice" onclick={() => choose(c.go)}>
				<span class="choice-icon"><Icon name={c.icon} size={19} /></span>
				<span class="choice-text">
					<strong>{c.label}</strong>
					<span>{c.sub}</span>
				</span>
				<Icon name="chevron-right" size={16} />
			</button>
		{/each}
	</div>
</Sheet>

<Sheet bind:open={jumpOpen} title="Jump to a date" width={400}>
	<form class="jump" onsubmit={jump}>
		<TextField label="Date" type="date" bind:value={jumpDate} min={today} max={addDays(today, MAX_DAYS - 1)} required />
		<Button type="submit" variant="accent" block loading={jumping}>Go to this day</Button>
	</form>
</Sheet>

<BlockTimeSheet
	bind:open={sheetOpen}
	timeZone={tz}
	block={editing}
	defaultDay={sheetDay}
	defaultRange={sheetRange}
	defaultAllDay={sheetAllDay}
	defaultLastDay={sheetLastDay}
	onsaved={() => refresh().catch(toasts.error)}
/>
<CalendarBusySheet bind:open={calendarOpen} interval={calendarInterval} onchanged={() => refresh().catch(toasts.error)} />
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
		gap: 16px;
	}
	.layout,
	.list-col,
	.week,
	.every-week {
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	.layout {
		gap: 16px;
	}
	.list-col {
		gap: 18px;
	}
	.week,
	.every-week {
		gap: 8px;
	}
	.eyebrow {
		padding: 0 6px;
	}
	.days {
		border-radius: var(--radius-card);
		padding: 4px;
		display: flex;
		flex-direction: column;
	}
	.day + .day {
		border-top: 1px solid var(--line);
	}
	.day {
		scroll-margin: 96px 0 calc(var(--tabbar-space) + 16px);
	}
	.none {
		font-size: 13px;
		color: var(--muted);
		padding: 0 6px;
	}
	.hint {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 14px;
		border-radius: 20px;
		color: var(--ink);
		text-decoration: none;
		font-size: 14px;
		font-weight: 600;
	}
	.hint-icon {
		flex: none;
		width: 36px;
		height: 36px;
		border-radius: 12px;
		display: grid;
		place-items: center;
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	.hint-text {
		flex: 1;
	}
	.end {
		min-height: 48px;
		display: grid;
		place-items: center;
		font-size: 13px;
		color: var(--muted);
	}

	/* The side panel only exists on wide screens; phones open the day in a sheet. */
	.panel-col {
		display: none;
	}

	.choices {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.choice {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 60px;
		padding: 8px 12px;
		border-radius: 18px;
		background: var(--surface);
		border: 1px solid var(--glass-border);
		text-align: left;
		color: var(--ink);
	}
	.choice:hover {
		border-color: var(--accent);
	}
	.choice-icon {
		flex: none;
		width: 38px;
		height: 38px;
		border-radius: 12px;
		display: grid;
		place-items: center;
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	.choice-text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.choice-text strong {
		font-size: 15px;
		font-weight: 650;
	}
	.choice-text span {
		font-size: 13px;
		color: var(--muted);
	}
	.jump {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}

	@media (min-width: 900px) {
		.layout {
			display: grid;
			grid-template-columns: minmax(0, 600px) minmax(360px, 520px);
			gap: 24px;
			align-items: start;
		}
		.panel-col {
			display: block;
			position: sticky;
			top: 20px;
		}
		.panel-col :global(.day-panel) {
			height: min(calc(100dvh - 40px), 820px);
		}
	}
</style>
