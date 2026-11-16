<script lang="ts">
	import { onMount, tick, untrack } from 'svelte';
	import { MediaQuery } from 'svelte/reactivity';
	import { invalidate, pushState, replaceState } from '$app/navigation';
	import { page } from '$app/state';
	import type { BusyInterval, ManualBlock } from '#lib/types';
	import { createBlock, deleteBlock } from '#lib/client/blocks';
	import { getMyBusy } from '#lib/client/me';
	import { addDays, addMonths, eachDay, endOfMonth, minutesIntoDay, startOfMonth, startOfWeek, zonedToIso } from '#lib/client/dates';
	import { weekdayName } from '#lib/client/format';
	import { live } from '#lib/client/live.svelte';
	import { toasts } from '#lib/client/toast.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon, { type IconName } from '#lib/components/ui/Icon.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';
	import BlockRow from '#lib/components/mytime/BlockRow.svelte';
	import BlockTimeSheet from '#lib/components/mytime/BlockTimeSheet.svelte';
	import CalendarBusySheet from '#lib/components/mytime/CalendarBusySheet.svelte';
	import MonthHeat from '#lib/components/mytime/MonthHeat.svelte';
	import AgendaDay from '#lib/components/mytime/AgendaDay.svelte';
	import WeekView from '#lib/components/mytime/WeekView.svelte';
	import { clock, layoutDays, rangeToIso } from '#lib/components/mytime/parts';
	import {
		agendaItems,
		busyRanges,
		CHUNK_DAYS,
		dayCellLabel,
		dayMarks,
		describeDay,
		dayNames,
		eveningPreset,
		freeLevel,
		freeSummary,
		groupWeeks,
		longestFree,
		type Range
	} from '#lib/components/mytime/days';

	let { data } = $props();

	const tz = $derived(data.user.timezone);
	const today = $derived(data.today);
	/** How far ahead you can look: today and the next 365 days. */
	const MAX_DAYS = 366;
	const lastDay = $derived(addDays(today, MAX_DAYS - 1));
	const wide = new MediaQuery('(min-width: 900px)', false);

	// ── Month or agenda, remembered on this device ─────────────────────────
	type View = 'month' | 'agenda';
	const VIEW_KEY = 'availabliss:my-time-view';
	let view = $state<View>('month');
	onMount(() => {
		try {
			if (localStorage.getItem(VIEW_KEY) === 'agenda') view = 'agenda';
		} catch {
			// Storage blocked: stay on the month.
		}
	});
	function toggleView() {
		view = view === 'month' ? 'agenda' : 'month';
		try {
			localStorage.setItem(VIEW_KEY, view);
		} catch {
			// Not remembered, but still switched.
		}
	}

	// ── Days, loaded a few weeks at a time ─────────────────────────────────
	/** Chunks after the first (which comes with the page), each CHUNK_DAYS long. */
	let more = $state<{ start: string; intervals: BusyInterval[] }[]>([]);
	let loadingMore = $state(false);
	let moreFor = untrack(() => data.today);
	/** The month on screen (first of the month). */
	let month = $state(untrack(() => startOfMonth(data.today)));
	// A new day (after midnight) starts again.
	$effect.pre(() => {
		if (moreFor !== today) {
			moreFor = today;
			more = [];
			if (month < startOfMonth(today)) month = startOfMonth(today);
		}
	});

	/** Busy time is loaded for today up to (not including) `end`. */
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

	/** A loaded day's busy time; null if it hasn't loaded yet. */
	function dayData(day: string) {
		if (day < today || day >= end) return null;
		return { timed: layout.timed.get(day) ?? [], allDay: layout.allDay.get(day) ?? [] };
	}

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

	/** Loads chunks until `day` has loaded. */
	async function ensureLoaded(day: string) {
		while (day >= end && !atLimit) {
			if (loadingMore) {
				await new Promise((r) => setTimeout(r, 100));
				continue;
			}
			const before = more.length;
			await loadMore();
			if (more.length === before) return;
		}
	}

	// The month on screen (and the next, for the next swipe) and the open week always have their busy time.
	$effect(() => {
		const wanted = weekDay ? addDays(startOfWeek(weekDay), 6) : endOfMonth(addMonths(month, 1));
		ensureLoaded(wanted < lastDay ? wanted : lastDay);
	});

	// In the agenda, load the next weeks as the end of the list comes into view.
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

	// ── The month's days ───────────────────────────────────────────────────
	function monthInfo(day: string) {
		const d = dayData(day);
		if (!d) return null;
		const busy = busyRanges(d.timed, d.allDay);
		return { level: freeLevel(busy), label: dayCellLabel(day, today, busy), mine: dayMarks(d.timed, d.allDay).mine };
	}

	// ── The week view: a history entry of its own, so the browser's back closes it ──
	const weekDay = $derived.by(() => {
		const d = page.state.myTimeDay;
		return d && d >= today && d <= lastDay ? d : '';
	});
	let returnScroll = 0;
	function openDay(day: string) {
		returnScroll = window.scrollY;
		pushState('', { myTimeDay: day });
		window.scrollTo(0, 0);
	}
	function selectDay(day: string) {
		replaceState('', { myTimeDay: day });
		// Paging the month along keeps the month you go back to near the week you looked at.
		if (view === 'month') month = startOfMonth(day);
	}
	function closeWeek() {
		history.back();
	}
	// Back where you were in the month or agenda.
	let wasOpen = false;
	$effect(() => {
		const open = !!weekDay;
		if (wasOpen && !open) tick().then(() => window.scrollTo(0, returnScroll));
		wasOpen = open;
	});

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
			{ label: 'Something else…', sub: 'Choose the times, or make it repeat', icon: 'clock', go: () => openNew(weekDay || today) }
		];
		return list;
	});
	function choose(go: () => void) {
		choicesOpen = false;
		go();
	}

	/** The agenda's free row: block that stretch (or the whole day). */
	const blockFree = (day: string, range: Range | null) => (range ? openNew(day, { range }) : openNew(day, { allDay: true }));

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

	const showCalendarHint = $derived(data.hasCalendar === false && !intervals.some((i) => i.source === 'calendar'));
</script>

<svelte:head><title>My time · Availabliss</title></svelte:head>

{#snippet everyWeek()}
	<section class="every-week" aria-labelledby="every-week">
		<h2 class="eyebrow" id="every-week">Repeating</h2>
		{#each repeating as block (block.id)}
			<BlockRow {block} timeZone={tz} onedit={() => openEdit(block)} ondelete={() => askDelete(block)} />
		{:else}
			<p class="none">Nothing repeats yet. Block time and choose a repeat for things like a class or practice.</p>
		{/each}
	</section>
{/snippet}

{#if weekDay}
	<div class="week-page">
		<WeekView
			day={weekDay}
			{today}
			last={lastDay}
			timeZone={tz}
			get={dayData}
			backLabel={view === 'month' ? 'Month' : 'Agenda'}
			onback={closeWeek}
			onday={selectDay}
			onselect={select}
			onopen={open}
			onblock={() => openNew(weekDay)}
		/>
	</div>
{:else}
	<div class="page">
		<PageHeader title="My time" subtitle="What groups see as busy" backHref="/app" backLabel="Home" backIcon="home">
			{#snippet actions()}
				<button type="button" class="glass switch" aria-label={view === 'month' ? 'Show as a list' : 'Show as a month'} onclick={toggleView}>
					<Icon name={view === 'month' ? 'list' : 'calendar'} size={16} strokeWidth={2.2} />
					<span>{view === 'month' ? 'Agenda' : 'Month'}</span>
				</button>
				<IconButton href="/app/calendars" icon="calendar-sync" label="Connected calendars" />
			{/snippet}
		</PageHeader>

		<div class="layout">
			<div class="top">
				<Button size="lg" variant="accent" icon="plus" block onclick={() => (choicesOpen = true)}>Block time</Button>

				{#if showCalendarHint}
					<a class="glass hint" href="/app/calendars">
						<span class="hint-icon"><Icon name="calendar-sync" size={18} /></span>
						<span class="hint-text">Connect a calendar so your busy time fills in by itself</span>
						<Icon name="chevron-right" size={16} />
					</a>
				{/if}
			</div>

			<div class="main">
				{#if view === 'month'}
					<div class="glass month-card">
						<MonthHeat {month} {today} last={lastDay} info={monthInfo} onmonth={(m) => (month = m)} onselect={openDay} />
					</div>
				{:else}
					{#each weeks as week, w (week.start)}
						<section class="week" aria-labelledby="week-{week.start}">
							<h2 class="eyebrow" id="week-{week.start}">{week.heading}</h2>
							<div class="glass days">
								{#each week.days as day (day)}
									{@const timed = layout.timed.get(day) ?? []}
									{@const allDay = layout.allDay.get(day) ?? []}
									{@const busy = busyRanges(timed, allDay)}
									<AgendaDay
										{day}
										{today}
										summary={freeSummary(busy)}
										label={describeDay(day, busy, dayNames(timed, allDay))}
										items={agendaItems(timed, allDay, data.blocks)}
										free={longestFree(busy)}
										onday={() => openDay(day)}
										onopen={open}
										onblock={(range) => blockFree(day, range)}
									/>
								{/each}
							</div>
						</section>
						{#if w === 0 && !wide.current}{@render everyWeek()}{/if}
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
				{/if}
			</div>

			{#if view === 'month' || wide.current}
				<div class="side">{@render everyWeek()}</div>
			{/if}
		</div>
	</div>
{/if}

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
	.top,
	.main,
	.week,
	.every-week {
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	.layout {
		gap: 18px;
	}
	.top,
	.main {
		gap: 18px;
	}
	.week,
	.every-week {
		gap: 8px;
	}
	.eyebrow {
		padding: 0 6px;
	}
	.switch {
		display: inline-flex;
		align-items: center;
		gap: 5px;
		height: 44px;
		padding: 0 12px 0 10px;
		border-radius: 999px;
		font-size: 13px;
		font-weight: 650;
		color: var(--ink);
	}
	.switch:hover {
		background: var(--surface);
	}
	.month-card {
		border-radius: var(--radius-card);
		padding: 12px 12px 14px;
	}
	.days {
		border-radius: var(--radius-card);
		padding: 4px;
		display: flex;
		flex-direction: column;
	}
	.days > :global(.day + .day) {
		border-top: 1px solid var(--line);
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
	.week-page {
		max-width: 760px;
		width: 100%;
		margin: 0 auto;
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

	/* Wide screens: the month or agenda in the main column, blocking and repeats beside it. */
	@media (min-width: 900px) {
		.layout {
			display: grid;
			grid-template-columns: minmax(0, 1fr) minmax(320px, 380px);
			grid-template-areas:
				'main top'
				'main side';
			grid-template-rows: auto 1fr;
			gap: 18px 24px;
			align-items: start;
		}
		.top {
			grid-area: top;
		}
		.main {
			grid-area: main;
		}
		.side {
			grid-area: side;
		}
		.month-card {
			padding: 18px;
			border-radius: 28px;
		}
	}
</style>
