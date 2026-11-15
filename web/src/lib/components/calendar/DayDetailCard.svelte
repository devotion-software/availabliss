<script lang="ts">
	import type { AvailabilityDay, DayAvailability, Member, Slot } from '#lib/types';
	import { formatDayKey, useFormat } from '#lib/client/format';
	import Button from '#lib/components/ui/Button.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import DayTimeline from './DayTimeline.svelte';
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
		onpropose: (slot: Slot) => void;
		/** Propose a day that doesn't work for everyone (the proposal sheet warns who's busy). */
		onproposeday?: (date: string) => void;
	}
	let { date, day, detail, slot, members, meId, noun, onpropose, onproposeday }: Props = $props();
	// The first time everyone's free that day, offered when no whole slot fits.
	const free = $derived(detail?.everyoneFree[0] ?? null);

	const fmt = useFormat();
	// Past days look like days outside the timing: nothing to plan.
	const past = $derived(date < fmt.current.today());
	const works = $derived(!past && day?.status === 'match' && slot !== null);
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
			text = t === '00:00' ? 'Everyone synced is free' : `Everyone synced is free from ${fmt.current.startsAt(slot.start).replace('From ', '')}`;
		} else if (day.status === 'busy') {
			const n = day.busyMemberIds.length;
			text = n ? `${busyNames(day.busyMemberIds, members)} ${n === 1 ? 'is' : 'are'} busy` : 'Someone is busy';
		} else if (day.status === 'free') text = "Everyone's free, but not for the whole timing";
		else if (day.status === 'hidden') text = "Outside this group's timing";
		return day.holiday ? `${text} · ${day.holiday}` : text;
	});
</script>

<section class="card glass" aria-live="polite" aria-label="Selected day">
	<div class="top">
		<div class="titles">
			<h3>{title}</h3>
			<span class="sub">{sub}</span>
		</div>
		<StatusPill tone={works ? 'mint' : 'neutral'}>{works ? 'Works' : past ? 'Past' : 'Doesn’t fit'}</StatusPill>
	</div>
	{#if past}
		<!-- Nothing to plan on a day that's gone. -->
	{:else if detail}
		<DayTimeline day={detail} {members} {meId} showDate={false} />
	{:else if day && day.status !== 'hidden'}
		<div class="loading"><Spinner size={18} label="Loading who's free" /></div>
	{/if}
	<div class="actions">
		{#if works && slot}
			<Button variant="accent" size="sm" class="act" onclick={() => onpropose(slot)}>Propose this {noun}</Button>
		{:else if free && !past}
			<Button variant="accent" size="sm" class="act" onclick={() => onpropose({ start: free.start, end: free.end, label: '' })}
				>Propose {fmt.current.time(free.start)} – {fmt.current.time(free.end)}</Button
			>
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
	.loading {
		display: flex;
		justify-content: center;
		padding: 20px 0;
	}
	.actions:empty {
		display: none;
	}
	.actions {
		display: flex;
		gap: 8px;
	}
	.actions :global(.act) {
		flex: 1;
		height: 44px;
		border-radius: 14px;
		font-size: 14px;
	}
</style>
