<script lang="ts">
	import type { AvailabilityDay, DayAvailability, Member, Slot } from '#lib/types';
	import { formatDayKey, useFormat } from '#lib/client/format';
	import Button from '#lib/components/ui/Button.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import DayTimeline from './DayTimeline.svelte';
	import { proposeLabel } from '#lib/client/requirements';
	import { busyNames } from './grid';

	/** What the selected day means for the group, who's free when, and the next steps. */
	interface Props {
		date: string;
		day: AvailabilityDay | undefined;
		/** Who's busy when on the day (null while loading). */
		detail: DayAvailability | null;
		slot: Slot | null;
		members: Map<string, Member>;
		meId: string;
		/** "weekend", "evening", … for "Propose this weekend". */
		noun: string;
		onpropose: (slot: Slot & { allDay?: boolean }) => void;
		/** Propose a day that doesn't work for everyone (the proposal sheet warns who's busy). */
		onproposeday?: (date: string) => void;
		/** Shortlisting the day's time for a date poll: whether it's picked, and picking / unpicking it. */
		shortlisted?: boolean;
		onshortlist?: (slot: Slot) => void;
	}
	let { date, day, detail, slot, members, meId, noun, onpropose, onproposeday, shortlisted = false, onshortlist }: Props = $props();
	// The first time everyone's free that day (else enough of the group, with a quorum), offered when no whole slot fits.
	const free = $derived(detail?.everyoneFree[0] ?? detail?.mostFree?.[0] ?? null);

	const fmt = useFormat();
	// Past days look like days outside the timing: nothing to plan.
	const past = $derived(date < fmt.current.today());
	const works = $derived(!past && day?.status === 'match' && slot !== null);
	/** Who's missing from the time that works (with a quorum or optional members). */
	const missing = $derived(works ? (slot?.missingMemberIds ?? []) : []);
	const partial = $derived(works && (day?.partial === true || missing.length > 0));
	const title = $derived.by(() => {
		const label = slot && works ? slot.label : formatDayKey(date);
		return date === fmt.current.today() && !works ? `Today · ${label}` : label;
	});
	const sub = $derived.by(() => {
		if (past) return 'This day is in the past';
		if (!day) return '';
		let text = '';
		if (works && slot) {
			const t = fmt.current.time(slot.start);
			const who = partial ? 'Most of the group is free' : 'Everyone synced is free';
			text = t === '00:00' ? who : `${who} from ${fmt.current.startsAt(slot.start).replace('From ', '')}`;
			if (missing.length) text += ` · ${busyNames(missing, members)} ${missing.length === 1 ? 'is' : 'are'} busy`;
		} else if (day.status === 'busy') {
			const n = day.busyMemberIds.length;
			text = n ? `${busyNames(day.busyMemberIds, members)} ${n === 1 ? 'is' : 'are'} busy` : 'Someone is busy';
		} else if (day.status === 'free') text = "Everyone's free, but not for the whole timing";
		else if (day.status === 'hidden') text = "Outside this group's timing";
		// A bank holiday has its own line below; school holidays are named here.
		return day.holiday && !day.bankHoliday ? `${text} · ${day.holiday}` : text;
	});
</script>

<section class="card glass" aria-live="polite" aria-label="Selected day">
	<div class="top">
		<div class="titles">
			<h3>{title}</h3>
			<span class="sub">{sub}</span>
		</div>
		<StatusPill tone={partial ? 'indigo' : works ? 'mint' : 'neutral'}>
			{partial ? 'Most free' : works ? 'Works' : past ? 'Past' : 'Doesn’t fit'}
		</StatusPill>
	</div>
	{#if day?.bankHoliday}
		<p class="bank"><Icon name="flag" size={13} />{day.holiday ? `${day.holiday} · Bank holiday` : 'Bank holiday'}</p>
	{/if}
	{#if !past && works && day?.preferredBy?.length}
		<p class="pref">
			<Icon name="heart" size={13} />
			{busyNames(day.preferredBy, members)}
			{day.preferredBy.length === 1 ? 'prefers' : 'prefer'} this
		</p>
	{/if}
	{#if past}
		<!-- Nothing to plan on a day that's gone. -->
	{:else if detail}
		<DayTimeline day={detail} {members} {meId} showDate={false} />
	{:else if day && day.status !== 'hidden'}
		<div class="loading"><Spinner size={18} label="Loading who's free" /></div>
	{/if}
	<div class="actions">
		{#if works && slot}
			<Button variant="accent" size="sm" class="act" onclick={() => onpropose(slot)}>
				{proposeLabel(noun, fmt.current.dayKey(slot.start) !== fmt.current.dayKey(new Date(Date.parse(slot.end) - 1).toISOString()))}
			</Button>
			{#if onshortlist}
				<Button
					variant="secondary"
					size="sm"
					class="act shortlist"
					icon={shortlisted ? 'check' : 'plus'}
					aria-pressed={shortlisted}
					onclick={() => onshortlist(slot)}
				>
					{shortlisted ? 'Shortlisted' : 'Shortlist'}
				</Button>
			{/if}
		{:else if free && !past}
			{@const toMidnight = fmt.current.dayKey(free.end) !== date}
			{@const allDay = toMidnight && fmt.current.dayKey(free.start) === date && fmt.current.time(free.start) === '00:00'}
			<Button
				variant="accent"
				size="sm"
				class="act"
				onclick={() => onpropose({ start: free.start, end: free.end, label: '', ...(allDay && { allDay: true }) })}
			>
				{allDay ? 'Propose all day' : `Propose ${fmt.current.time(free.start)} – ${toMidnight ? 'midnight' : fmt.current.time(free.end)}`}
			</Button>
		{:else if !past && onproposeday && (day?.status === 'busy' || day?.status === 'free')}
			<Button variant="secondary" size="sm" class="act" onclick={() => onproposeday(date)}>Propose anyway</Button>
		{/if}
	</div>
</section>

<style>
	.card {
		border-radius: 22px;
		padding: 14px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
	}
	.titles {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	h3 {
		font-size: 16px;
		font-weight: 650;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
	}
	.bank {
		display: flex;
		align-items: center;
		gap: 6px;
		margin: 0;
		font-size: 13px;
		font-weight: 600;
		color: var(--ink);
	}
	.pref {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 13px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	.loading {
		display: flex;
		justify-content: center;
		padding: 20px 0;
	}
	.actions:empty {
		display: none;
	}
	/* On narrow phones Shortlist drops below Propose rather than pushing the card wider. */
	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}
	.actions :global(.act.shortlist) {
		padding: 0 14px;
	}
	.actions :global(.act) {
		flex: 1 0 auto;
		height: 44px;
		border-radius: 14px;
		font-size: 14px;
	}
</style>
