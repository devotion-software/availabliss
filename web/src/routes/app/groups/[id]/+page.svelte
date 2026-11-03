<script lang="ts">
	import { untrack } from 'svelte';
	import type { DayAvailability, Slot } from '#lib/types';
	import { getAvailability, getDayAvailability } from '#lib/client/availability';
	import { addDays, addMonths, endOfMonth, startOfMonth } from '#lib/client/dates';
	import { formatDayKey, formatDayRange, monthTitle, useFormat } from '#lib/client/format';
	import { live } from '#lib/client/live.svelte';
	import { groupSubtitle, isAdminRole, memberMap } from '#lib/client/members';
	import { detailLabel, lengthLabel, PRESETS, presetOf } from '#lib/client/requirements';
	import { toasts } from '#lib/client/toast.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import MonthGrid from '#lib/components/calendar/MonthGrid.svelte';
	import WeekGrid from '#lib/components/calendar/WeekGrid.svelte';
	import DayTimeline from '#lib/components/calendar/DayTimeline.svelte';
	import DayDetailCard from '#lib/components/calendar/DayDetailCard.svelte';
	import { dayMap, slotOn, visibleColumns, weekDates } from '#lib/components/calendar/grid';
	import SyncBanner from '#lib/components/group/SyncBanner.svelte';
	import CoverageNote from '#lib/components/group/CoverageNote.svelte';
	import BestOptions from '#lib/components/group/BestOptions.svelte';
	import PlannerSheet from '#lib/components/group/PlannerSheet.svelte';
	import { getPlan } from '#lib/client/plan';
	import { upgradeSheet } from '#lib/client/ui-state.svelte';
	import { DESKTOP, layoutQuery } from '#lib/client/media.svelte';
	import ProposeSheet from '#lib/components/events/ProposeSheet.svelte';
	import Breadcrumbs from '#lib/components/ui/Breadcrumbs.svelte';
	import ChatPanel from '#lib/components/chat/ChatPanel.svelte';

	let { data } = $props();

	type View = 'month' | 'week' | 'day';
	const VIEWS = [
		{ value: 'month', label: 'Month' },
		{ value: 'week', label: 'Week' },
		{ value: 'day', label: 'Day' }
	] as const;

	const fmt = useFormat();
	const group = $derived(data.group);
	const members = $derived(memberMap(group));
	const isAdmin = $derived(isAdminRole(group.role));
	const noun = $derived(PRESETS.find((p) => p.id === presetOf(group.requirement))?.noun ?? 'time');
	const base = $derived(`/app/groups/${group.id}`);

	let view = $state<View>('month');
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
			month = data.month;
			availability = next;
			selected = defaultSelection(next, data.month);
		});
	});
	let loadingMonth = $state(false);
	let weekDays = $state(new Map<string, DayAvailability | null>());
	let dayDetail = $state<DayAvailability | null>(null);

	const days = $derived(dayMap(availability.days));
	const columns = $derived(visibleColumns(availability));
	const selectedSlot = $derived(slotOn(availability.slots, selected, fmt.current.dayKey));
	const week = $derived(weekDates(selected, columns));
	const heading = $derived(
		view === 'month' ? monthTitle(month) : view === 'week' ? formatDayRange(week[0], week[week.length - 1], false) : formatDayKey(selected)
	);

	// Phones have room for a terse heading only: "Nov 2026", "27–29 Nov", "Fri 27".
	const shortHeading = $derived(
		view === 'month'
			? monthTitle(month).replace(/^(\w{3})\w*/, '$1')
			: view === 'week'
				? formatDayRange(week[0], week[week.length - 1], false).replace(' – ', '–')
				: formatDayKey(selected).split(' ').slice(0, 2).join(' ')
	);

	/** First day that works this month, else today if it's in the month, else the 1st. */
	function defaultSelection(a: typeof data.availability, m: string): string {
		const today = fmt.current.today();
		const match = a.days.find((d) => d.status === 'match' && d.date >= today);
		if (match) return match.date;
		return today.slice(0, 7) === m.slice(0, 7) ? today : m;
	}

	let monthRequest = 0;
	async function loadMonth(m: string) {
		const id = ++monthRequest;
		loadingMonth = true;
		try {
			const next = await getAvailability(group.id, m, endOfMonth(m));
			if (id !== monthRequest) return;
			availability = next;
			month = m;
		} catch (err) {
			toasts.error(err);
		} finally {
			if (id === monthRequest) loadingMonth = false;
		}
	}

	let detailRequest = 0;
	async function loadDetails() {
		const id = ++detailRequest;
		try {
			if (view === 'week') {
				const dates = week;
				weekDays = new Map();
				const results = await Promise.all(
					dates.map((d) => (days.get(d)?.status === 'hidden' ? null : getDayAvailability(group.id, d).catch(() => null)))
				);
				if (id === detailRequest) weekDays = new Map(dates.map((d, i) => [d, results[i]]));
			} else if (view === 'day') {
				dayDetail = null;
				const detail = await getDayAvailability(group.id, selected);
				if (id === detailRequest) dayDetail = detail;
			}
		} catch (err) {
			toasts.error(err);
		}
	}

	// Week and day views fetch per-day detail for what's on screen. Only the view,
	// the selected day and the group trigger it (loadDetails also reads the month's
	// availability, which would otherwise refetch the week every time that reloads).
	$effect(() => {
		void view;
		void selected;
		void group.id;
		untrack(() => {
			if (view !== 'month') loadDetails();
		});
	});

	// Someone's busy time, or the group's timing, changed: refresh what's visible.
	$effect(() => {
		const id = group.id;
		const refresh = () =>
			untrack(() => {
				loadMonth(month);
				if (view !== 'month') loadDetails();
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

	function select(date: string) {
		selected = date;
		if (startOfMonth(date) !== month) loadMonth(startOfMonth(date));
	}

	function step(direction: 1 | -1) {
		if (view === 'month') {
			const m = addMonths(month, direction);
			loadMonth(m);
			selected = m;
		} else {
			select(addDays(selected, direction * (view === 'week' ? 7 : 1)));
		}
	}

	let proposeOpen = $state(false);
	let proposeInitial = $state<(Slot & { title?: string; note?: string }) | { day: string } | null>(null);
	function propose(slot: (Slot & { title?: string; note?: string }) | { day: string } | null) {
		proposeInitial = slot;
		proposeOpen = true;
	}

	// The optional AI planner: only offered when the server has it set up; Pro to use.
	let aiPlanner = $state<{ available: boolean; included: boolean } | null>(null);
	// Reload whenever the upgrade sheet closes, so buying Pro unlocks it straight away.
	$effect(() => {
		if (upgradeSheet.open) return;
		let stale = false;
		getPlan()
			.then((p) => !stale && (aiPlanner = p.aiPlanner))
			.catch(() => {});
		return () => {
			stale = true;
		};
	});
	let plannerOpen = $state(false);
	function openPlanner() {
		if (aiPlanner?.included) plannerOpen = true;
		else upgradeSheet.showFor('AI planning is part of Pro: it picks the best time, suggests things to do and drafts the plan.');
	}

	// Mount one layout's pieces, not both. Until hydrated, both render and CSS picks.
	const isDesktop = layoutQuery(DESKTOP);
	const desk = $derived(isDesktop.matches !== false);
	const phone = $derived(isDesktop.matches !== true);

	function selectSlot(slot: Slot) {
		select(fmt.current.dayKey(slot.start));
		view = 'month';
	}
</script>

<svelte:head><title>{group.name} · Availabliss</title></svelte:head>

<div class="page">
	<div class="main">
		{#if phone}
		<!-- Phone header (GroupCalendar.dc) -->
		<header class="phone-head">
			<IconButton href="/app" icon="chevron-left" label="Back to groups" />
			<div class="titles">
				<h1>{group.name}</h1>
				<span class="sub">{groupSubtitle(group)}</span>
			</div>
			<IconButton href="{base}/members" icon="user-plus" label="Members and invites" />
		</header>
		{/if}

		{#if desk}
		<!-- Desktop header (Web.dc) -->
		<header class="desk-head">
			<div class="titles">
				<Breadcrumbs items={[{ label: 'Groups', href: '/app' }, { label: group.name }]} />
				<h1>{group.name}</h1>
				<span class="sub">
					{lengthLabel(group.requirement)} · {detailLabel(group.requirement)} ·
					<a href="{base}/timing">{isAdmin ? 'Edit timing' : 'See timing'}</a> ·
					<a href="{base}/members">{group.members.length} members</a>
				</span>
			</div>
			<SegmentedControl options={VIEWS} bind:value={view} label="Calendar view" />
		</header>
		{/if}

		{#if phone}
		<div class="phone-only stack">
			<SyncBanner groupId={group.id} members={group.members} canNudge={isAdmin} />
			<a class="timing glass" href="{base}/timing">
				<Icon name="clock" size={16} />
				<span class="timing-text"><strong>{lengthLabel(group.requirement)}</strong> · {detailLabel(group.requirement)}</span>
				<Icon name="pencil" size={15} class="muted" />
			</a>
		</div>
		{/if}

		<section class="cal" aria-labelledby="cal-heading">
			<div class="cal-head">
				<h2 id="cal-heading"><span class="desk-inline">{heading}</span><span class="phone-inline">{shortHeading}</span></h2>
				{#if loadingMonth}<Spinner size={16} label="Loading month" />{/if}
				{#if desk}<span class="desk-only"><SyncBanner groupId={group.id} members={group.members} canNudge={isAdmin} variant="pill" /></span>{/if}
				<span class="nav desk-flex">
					<IconButton icon="chevron-left" label="Previous {view}" variant="surface" shape="rounded" size={40} onclick={() => step(-1)} />
					<IconButton icon="chevron-right" label="Next {view}" variant="surface" shape="rounded" size={40} onclick={() => step(1)} />
				</span>
				<span class="nav phone-flex">
					<IconButton icon="chevron-left" label="Previous {view}" variant="plain" shape="rounded" size={36} onclick={() => step(-1)} />
					<IconButton icon="chevron-right" label="Next {view}" variant="plain" shape="rounded" size={36} onclick={() => step(1)} />
				</span>
				<span class="phone-only">
					<SegmentedControl options={VIEWS} bind:value={view} label="Calendar view" size="sm" />
				</span>
			</div>

			<CoverageNote gaps={availability.checkedUntil ?? []} {members} meId={data.user.id} />

			<div class="cal-body" class:boxed={view !== 'month'}>
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
			{:else if view === 'week'}
				<WeekGrid
					dates={week}
					days={weekDays}
					{members}
					onselect={(d) => {
						selected = d;
						view = 'day';
					}}
				/>
			{:else if dayDetail}
				<DayTimeline day={dayDetail} {members} meId={data.user.id} />
			{:else}
				<div class="loading"><Spinner label="Loading day" /></div>
			{/if}
			</div>
		</section>

		{#if view === 'month'}
			<DayDetailCard
				date={selected}
				day={days.get(selected)}
				slot={selectedSlot}
				{members}
				{noun}
				onpropose={propose}
				onproposeday={(day) => propose({ day })}
				onseeday={() => (view = 'day')}
			/>
		{/if}
	</div>

	<aside class="aside">
		<BestOptions
			slots={availability.slots}
			pendingCount={availability.pendingMemberIds.length}
			onpropose={propose}
			onselect={selectSlot}
			onplan={aiPlanner?.available ? openPlanner : undefined}
		/>
		<!-- Only mounted on desktop: it loads and subscribes to the chat. Phones use the Chat tab. -->
		{#if isDesktop.matches}
			<div class="desk-only">
				<ChatPanel {group} me={data.user} onpropose={() => propose(null)} />
			</div>
		{/if}
	</aside>
</div>

<ProposeSheet bind:open={proposeOpen} {group} meId={data.user.id} initial={proposeInitial} />
<PlannerSheet bind:open={plannerOpen} groupId={group.id} onpropose={({ slot, title, note }) => propose({ ...slot, title, note })} />

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

	.phone-head,
	.desk-head {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.desk-head {
		display: none;
		align-items: flex-end;
		gap: 14px;
		flex-wrap: wrap;
	}
	.titles {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	h1 {
		font-size: 22px;
		font-weight: 700;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
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
	.cal-body {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.cal-body.boxed {
		background: var(--glass);
		-webkit-backdrop-filter: var(--blur);
		backdrop-filter: var(--blur);
		border: 1px solid var(--glass-border);
		box-shadow: var(--glass-shadow);
		border-radius: 22px;
		padding: 12px;
	}
	.cal-head {
		display: flex;
		align-items: center;
		gap: 8px;
		flex-wrap: wrap;
	}
	h2 {
		flex: 1;
		font-size: 19px;
		font-weight: 650;
		white-space: nowrap;
	}
	.cal-head {
		flex-wrap: nowrap;
	}
	.cal-head h2 {
		flex: 0 1 auto;
	}
	.cal-head > .phone-only:last-child {
		margin-left: auto;
	}
	.desk-inline,
	.nav.desk-flex {
		display: none;
	}
	.cal-head > .phone-only:last-child {
		flex: none;
	}
	.nav.phone-flex {
		display: flex;
		gap: 0;
		margin-left: -4px;
	}
	.nav {
		display: flex;
		gap: 6px;
	}
	.loading {
		display: flex;
		justify-content: center;
		padding: 40px;
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
		.phone-head,
		.phone-only,
		.phone-only.stack,
		.phone-only:not(.stack) {
			display: none;
		}
		.desk-only {
			display: block;
		}
		.desk-inline {
			display: inline;
		}
		.phone-inline,
		.nav.phone-flex {
			display: none;
		}
		.nav.desk-flex {
			display: flex;
		}
		.desk-head {
			display: flex;
		}
		h1 {
			font-size: 34px;
			font-weight: 750;
			line-height: 1.05;
			white-space: normal;
		}
		.desk-head .sub {
			font-size: 14px;
			margin-top: 4px;
		}
		.main {
			gap: 14px;
		}
		.cal-body.boxed {
			background: none;
			-webkit-backdrop-filter: none;
			backdrop-filter: none;
			border: 0;
			box-shadow: none;
			border-radius: 0;
			padding: 0;
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
		.cal-head h2 {
			flex: 1;
			font-size: 20px;
		}
		.aside {
			max-width: 380px;
			position: sticky;
			top: 20px;
		}
	}
</style>
