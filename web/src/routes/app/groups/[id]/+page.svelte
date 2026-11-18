<script lang="ts">
	import { untrack } from 'svelte';
	import type { DayAvailability, Slot } from '#lib/types';
	import { addDays, addMonths, endOfMonth, startOfMonth, startOfWeek } from '#lib/client/dates';
	import { formatDayRange, monthTitle, useFormat } from '#lib/client/format';
	import { live } from '#lib/client/live.svelte';
	import { isAdminRole, memberMap } from '#lib/client/members';
	import { detailLabel, lengthLabel, PRESETS, presetOf } from '#lib/client/requirements';
	import { toasts } from '#lib/client/toast.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import MonthGrid from '#lib/components/calendar/MonthGrid.svelte';
	import WeekGrid from '#lib/components/calendar/WeekGrid.svelte';
	import DayDetailCard from '#lib/components/calendar/DayDetailCard.svelte';
	import { dayMap, findFreeDay, slotOn, visibleColumns, weekDates } from '#lib/components/calendar/grid';
	import SyncBanner from '#lib/components/group/SyncBanner.svelte';
	import CoverageNote from '#lib/components/group/CoverageNote.svelte';
	import BestOptions from '#lib/components/group/BestOptions.svelte';
	import NextCard from '#lib/components/group/NextCard.svelte';
	import GroupHeader from '#lib/components/group/GroupHeader.svelte';
	import CalendarHeading from '#lib/components/group/CalendarHeading.svelte';
	import CalendarPager from '#lib/components/group/CalendarPager.svelte';
	import ShortlistBar from '#lib/components/group/ShortlistBar.svelte';
	import GroupSheets from '#lib/components/group/GroupSheets.svelte';
	import { AvailabilityCache } from '#lib/components/group/availability-cache';
	import { Shortlist } from '#lib/components/group/shortlist.svelte';
	import { GroupSheetState } from '#lib/components/group/sheets.svelte';
	import type { CalendarView } from '#lib/components/group/views';
	import { DESKTOP, layoutQuery } from '#lib/client/media.svelte';
	import ChatPanel from '#lib/components/chat/ChatPanel.svelte';
	import PlansCard from '#lib/components/proposals/PlansCard.svelte';

	let { data } = $props();

	const fmt = useFormat();
	const group = $derived(data.group);
	const members = $derived(memberMap(group));
	const isAdmin = $derived(isAdminRole(group.role));
	const noun = $derived(PRESETS.find((p) => p.id === presetOf(group.requirement))?.noun ?? 'time');
	const base = $derived(`/app/groups/${group.id}`);

	// The Propose and AI planner sheets, and the times shortlisted for a date poll.
	const sheets = new GroupSheetState();
	const propose = sheets.propose;
	const shortlist = new Shortlist();
	// A new group starts with an empty shortlist.
	$effect(() => {
		void group.id;
		untrack(() => shortlist.clear());
	});

	// Months and days already fetched or on their way; cleared when something changes (busy time, the timing).
	const cache = new AvailabilityCache(() => group.id);

	// The selected day's detail (who's free when, and Propose) shows under either view.
	let view = $state<CalendarView>('month');
	// Seeded from this page's load; navigation overrides them client-side. Not
	// $derived from `data`: that object changes whenever a parent layout reloads
	// (live events in any group refetch the groups list), which would snap the
	// calendar back to this month. Only a rerun of this page's own load reseeds.
	let month = $state(untrack(() => data.month));
	let availability = $state(untrack(() => data.availability));
	let selected = $state(untrack(() => defaultSelection(data.availability, data.month)));
	let seededFrom = untrack(() => data.availability);
	$effect.pre(() => {
		const next = data.availability;
		if (next === seededFrom) return;
		seededFrom = next;
		untrack(() => {
			cache.forget();
			month = data.month;
			availability = next;
			selected = defaultSelection(next, data.month);
			cache.seed(data.month, next);
			findFreeNeighbours();
		});
	});
	let loadingMonth = $state(false);
	let weekDays = $state(new Map<string, DayAvailability | null>());
	let dayDetail = $state<DayAvailability | null>(null);

	const days = $derived(dayMap(availability.days));
	const columns = $derived(visibleColumns(availability));
	const selectedSlot = $derived(slotOn(availability.slots, selected, fmt.current.dayKey));
	const week = $derived(weekDates(selected, columns));
	const heading = $derived(view === 'month' ? monthTitle(month) : formatDayRange(week[0], week[week.length - 1], false));

	// Phones have room for a terse heading only: "Nov 2026", "27–29 Nov".
	const shortHeading = $derived(
		view === 'month' ? monthTitle(month).replace(/^(\w{3})\w*/, '$1') : formatDayRange(week[0], week[week.length - 1], false).replace(' – ', '–')
	);

	/** First day that works this month, else today if it's in the month, else the 1st. */
	function defaultSelection(a: typeof data.availability, m: string): string {
		const today = fmt.current.today();
		const match = a.days.find((d) => d.status === 'match' && d.date >= today);
		if (match) return match.date;
		return today.slice(0, 7) === m.slice(0, 7) ? today : m;
	}

	$effect(() => untrack(() => cache.seed(month, availability)));

	let monthRequest = 0;
	async function loadMonth(m: string) {
		const id = ++monthRequest;
		// Already fetched (usually: neighbours are prefetched): show it as the page slides in.
		const ready = cache.monthsReady.get(m);
		if (ready) {
			availability = ready;
			month = m;
			loadingMonth = false;
		} else {
			month = m;
			loadingMonth = true;
			try {
				const next = await cache.month(m);
				if (id !== monthRequest) return;
				availability = next;
			} catch (err) {
				toasts.error(err);
				return;
			} finally {
				if (id === monthRequest) loadingMonth = false;
			}
		}
		cache.prefetchAround(m);
	}

	let weekRequest = 0;
	let weekKey = '';
	async function loadWeek(force = false) {
		const dates = week;
		const key = dates.join();
		if (!force && key === weekKey) return;
		weekKey = key;
		const id = ++weekRequest;
		const wanted = (d: string) => days.get(d)?.status !== 'hidden';
		// Cached (usually: the next week is prefetched): show it at once.
		weekDays = dates.every((d) => !wanted(d) || cache.daysReady.has(d)) ? new Map(dates.map((d) => [d, cache.daysReady.get(d) ?? null])) : new Map();
		const results = await Promise.all(dates.map((d) => (wanted(d) ? cache.day(d).catch(() => null) : null)));
		// A week running into the next (or previous) month needs that month's days too, to fade the ones that don't work.
		await Promise.all([...new Set(dates.map(startOfMonth))].filter((m) => m !== month).map((m) => cache.month(m).catch(() => null)));
		if (id === weekRequest) weekDays = new Map(dates.map((d, i) => [d, results[i]]));
		// The next week the user is likely to swipe to.
		for (const d of dates) cache.day(addDays(d, 7 * lastStep)).catch(() => {});
	}

	let dayRequest = 0;
	async function loadDay() {
		const id = ++dayRequest;
		const date = selected;
		if (dayDetail?.date !== date) dayDetail = cache.daysReady.get(date) ?? null;
		try {
			const detail = await cache.day(date);
			if (id === dayRequest) dayDetail = detail;
		} catch (err) {
			toasts.error(err);
		}
	}

	function loadDetails(force = false) {
		loadDay();
		if (view === 'week') loadWeek(force);
	}

	// Per-day detail for what's on screen: the selected day, plus the week in week view. Only
	// the view, the selected day and the group trigger it (loading also reads the month's
	// availability, which would otherwise refetch every time that reloads).
	$effect(() => {
		void view;
		void selected;
		void group.id;
		untrack(() => loadDetails());
	});

	// Someone's busy time, or the group's timing, changed: refresh what's visible.
	$effect(() => {
		const id = group.id;
		const refresh = () =>
			untrack(() => {
				cache.forget();
				loadMonth(month);
				loadDetails(true);
				findFreeNeighbours();
			});
		const off = live.on(['availability.changed', 'group.updated'], (e) => {
			if (e.groupId === null || e.groupId === id) refresh();
		});
		// Changes made while the stream was down never arrived.
		const offReconnect = live.onReconnect(refresh);
		return () => {
			off();
			offReconnect();
		};
	});

	/** Whether a day works for everyone (and isn't over); unknown days count as working, so nothing fades while it loads. */
	function works(date: string): boolean {
		if (date < todayKey) return false;
		const status = (days.get(date) ?? cache.monthsReady.get(startOfMonth(date))?.days.find((d) => d.date === date))?.status;
		return status === undefined || status === 'match';
	}

	function select(date: string) {
		selected = date;
		if (startOfMonth(date) !== month) loadMonth(startOfMonth(date));
	}

	// The month or week on screen doesn't include today, so "Today" is offered.
	const todayKey = $derived(fmt.current.today());
	const awayFromToday = $derived(view === 'month' ? month !== startOfMonth(todayKey) : startOfWeek(selected) !== startOfWeek(todayKey));
	/** Back to today's month or week, with today selected. */
	function jumpToToday() {
		const today = todayKey;
		lastStep = today > selected ? 1 : -1;
		if (startOfMonth(today) !== month) loadMonth(startOfMonth(today));
		selected = today;
	}

	// The nearest dates that work for everyone before / after the month or week on screen (the
	// first day of a run that works, from today on, within the group's search range). Null when
	// there's none that way; `findingFree` while it's still looking.
	let previousFree = $state<string | null>(null);
	let nextFree = $state<string | null>(null);
	let findingFree = $state(true);
	let freeRequest = 0;
	async function findFreeNeighbours() {
		const id = ++freeRequest;
		const { from, to } = group.requirement.range;
		const start = view === 'month' ? month : startOfWeek(selected);
		const end = view === 'month' ? endOfMonth(month) : addDays(start, 6);
		const today = todayKey;
		const after = addDays(end, 1);
		const [next, previous] = await Promise.all([
			findFreeDay(cache.month, after > today ? after : today, to, true),
			findFreeDay(cache.month, from > today ? from : today, addDays(start, -1), false)
		]);
		if (id !== freeRequest) return;
		nextFree = next;
		previousFree = previous;
		findingFree = false;
	}
	$effect(() => {
		void pageKey;
		void group.requirement.range.from;
		void group.requirement.range.to;
		untrack(() => findFreeNeighbours());
	});
	/** To the month or week holding a free date either side, with it selected. */
	function jumpTo(date: string) {
		lastStep = date > selected ? 1 : -1;
		if (startOfMonth(date) !== month) loadMonth(startOfMonth(date));
		selected = date;
	}

	let lastStep: 1 | -1 = 1;
	function step(direction: 1 | -1) {
		lastStep = direction;
		if (view === 'month') {
			const m = addMonths(month, direction);
			loadMonth(m);
			selected = m;
		} else {
			select(addDays(selected, direction * 7));
		}
	}

	// One page per month / week.
	const pageKey = $derived(`${view}:${view === 'month' ? month : (week[0] ?? selected)}`);

	// Mount one layout's pieces, not both. Until hydrated, both render and CSS picks.
	const isDesktop = layoutQuery(DESKTOP);
	const desk = $derived(isDesktop.matches !== false);
	// Narrow phones in portrait: the week as cards.
	const narrow = layoutQuery('(max-width: 599px)');
	const phoneWidth = $derived(narrow.matches === true);
	const phone = $derived(isDesktop.matches !== true);

	function selectSlot(slot: Slot) {
		select(fmt.current.dayKey(slot.start));
		view = 'month';
	}
</script>

<svelte:head><title>{group.name} · Availabliss</title></svelte:head>

<div class="page" class:with-bar={shortlist.slots.length > 0}>
	<div class="main">
		{#if desk}
			<GroupHeader {group} {isAdmin} bind:view />
		{/if}

		{#if phone}
			<div class="phone-only stack">
				<SyncBanner groupId={group.id} members={group.members} canNudge={isAdmin} />
				<a class="timing glass" href="{base}/timing">
					<Icon name="clock" size={16} />
					<span class="timing-text"><strong>{lengthLabel(group.requirement)}</strong> · {detailLabel(group.requirement)}</span>
					<Icon name="pencil" size={15} class="muted" />
				</a>
				<PlansCard {group} meId={data.user.id} />
				<NextCard {group} onpropose={propose} />
			</div>
		{/if}

		<section class="cal" aria-labelledby="cal-heading">
			<CalendarHeading
				bind:view
				{heading}
				{shortHeading}
				{loadingMonth}
				{desk}
				{group}
				{isAdmin}
				{awayFromToday}
				{previousFree}
				{nextFree}
				{findingFree}
				onstep={step}
				ontoday={jumpToToday}
				onjump={jumpTo}
			/>

			<CoverageNote gaps={availability.checkedUntil ?? []} {members} meId={data.user.id} />

			<CalendarPager {pageKey} boxed={view === 'week' && !(!desk && phoneWidth)} onstep={step}>
				{#if view === 'month'}
					{#if phone}
						<span class="phone-only">
							<MonthGrid {availability} {month} {members} {selected} onselect={select} compact />
						</span>
					{/if}
					{#if desk}
						<span class="desk-only">
							<MonthGrid {availability} {month} {members} {selected} onselect={select} />
						</span>
					{/if}
				{:else}
					<!-- Phones get a card per day; the time grid needs a wider screen. -->
					<WeekGrid dates={week} days={weekDays} {members} {selected} onselect={select} cards={!desk && phoneWidth} {works} />
				{/if}
			</CalendarPager>
		</section>

		<DayDetailCard
			date={selected}
			day={days.get(selected)}
			detail={dayDetail?.date === selected ? dayDetail : null}
			slot={selectedSlot}
			{members}
			meId={data.user.id}
			{noun}
			onpropose={propose}
			onproposeday={(day) => propose({ day })}
			shortlisted={selectedSlot ? shortlist.has(selectedSlot) : false}
			onshortlist={shortlist.toggle}
		/>
	</div>

	<aside class="aside">
		{#if desk}
			<div class="desk-only"><NextCard {group} onpropose={propose} /></div>
		{/if}
		<BestOptions
			slots={availability.slots}
			{members}
			pendingCount={availability.pendingMemberIds.length}
			onpropose={propose}
			onselect={selectSlot}
			onplan={sheets.aiPlanner?.available ? sheets.openPlanner : undefined}
			picked={shortlist.has}
			onpick={shortlist.toggle}
		/>
		<!-- Only mounted on desktop: it loads and subscribes to the chat. Phones open the chat from the header. -->
		{#if isDesktop.matches}
			<div class="desk-only">
				<ChatPanel {group} me={data.user} onpropose={() => propose(null)} />
			</div>
		{/if}
	</aside>
</div>

<ShortlistBar {shortlist} groupId={group.id} />
<GroupSheets {sheets} {group} meId={data.user.id} />

<style>
	.page {
		display: flex;
		flex-wrap: wrap;
		gap: 20px;
		align-items: flex-start;
	}
	.main {
		flex: 999 1 520px;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.aside {
		flex: 1 1 300px;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.stack {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	.timing {
		min-height: 44px;
		border-radius: 14px;
		padding: 0 12px;
		display: flex;
		align-items: center;
		gap: 8px;
		text-decoration: none;
		color: var(--ink);
		font-size: 13px;
	}
	.timing:hover {
		color: var(--ink);
	}
	.timing-text {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.timing strong {
		font-weight: 600;
	}

	.cal {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	/* Also matched the calendar's inner page before it moved into CalendarPager; kept as it was. */
	.page {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.desk-only {
		display: none;
	}
	.phone-only {
		display: contents;
	}
	.phone-only:not(.stack) {
		display: block;
	}
	.phone-only.stack {
		display: flex;
	}

	@media (min-width: 900px) {
		.phone-only,
		.phone-only.stack,
		.phone-only:not(.stack) {
			display: none;
		}
		.desk-only {
			display: block;
		}
		.main {
			gap: 14px;
		}
		.cal {
			background: var(--glass);
			-webkit-backdrop-filter: var(--blur);
			backdrop-filter: var(--blur);
			border: 1px solid var(--glass-border);
			box-shadow: var(--glass-shadow);
			border-radius: 28px;
			padding: 18px;
			gap: 12px;
		}
		.aside {
			max-width: 380px;
			position: sticky;
			top: 20px;
		}
	}
	.with-bar {
		padding-bottom: 76px;
	}
</style>
